import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import Anthropic from "@anthropic-ai/sdk";
import {
  boundText, buildUserPrompt, dayKey, parseVerdict, SYSTEM_PROMPT, validateReport, withinBudget,
} from "./moderation";

admin.initializeApp();
const db = admin.firestore();

// Cost controls — override with function environment variables if needed
const DAILY_TOTAL_LIMIT = Number(process.env.MODERATION_DAILY_LIMIT ?? 500);
const DAILY_PER_REPORTER_LIMIT = Number(process.env.MODERATION_PER_REPORTER_LIMIT ?? 10);

// Small, cheap model; one short classification per report
const MODEL = "claude-haiku-4-5";

const anthropic = () =>
  new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY, timeout: 20_000, maxRetries: 1 });

/**
 * Atomically reserves one AI review in today's budget (global + per reporter).
 * Usage docs live in `moderationUsage`, which clients cannot read or write.
 */
async function reserveBudget(reporterId: string): Promise<boolean> {
  const ref = db.collection("moderationUsage").doc(dayKey(new Date()));
  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const usage = snap.data() as { total?: number; perReporter?: Record<string, number> } | undefined;
    if (!withinBudget(usage, reporterId, { dailyTotal: DAILY_TOTAL_LIMIT, perReporter: DAILY_PER_REPORTER_LIMIT })) {
      return false;
    }
    tx.set(ref, {
      total: admin.firestore.FieldValue.increment(1),
      perReporter: { [reporterId]: admin.firestore.FieldValue.increment(1) },
    }, { merge: true });
    return true;
  });
}

/**
 * AI review of a newly created report.
 *
 * Private chats are end-to-end encrypted, so the server never has their plaintext. The model only
 * sees text when (a) the message is in the public global chat — read here, server-side, so it can't
 * be forged — or (b) the reporter explicitly chose to share that one message's text, which is stored
 * as reporter-supplied and unverified. Everything else goes to manual review.
 */
export const reviewReport = functions
  .runWith({ secrets: ["ANTHROPIC_API_KEY"], timeoutSeconds: 60, maxInstances: 5 })
  .firestore.document("reports/{reportId}")
  .onCreate(async (snap) => {
    const report = validateReport(snap.data());
    if (!report) {
      await snap.ref.update({ status: "rejected_invalid", reviewedAt: admin.firestore.FieldValue.serverTimestamp() });
      return;
    }

    let sourceText = "";
    let textSource: "server_global_chat" | "reporter_supplied_unverified" | "none" = "none";
    if (report.chatType === "global") {
      const msg = await db.collection("globalChat").doc(report.messageId).get();
      sourceText = msg.exists ? String(msg.get("text") ?? "") : "";
      if (sourceText) textSource = "server_global_chat";
    } else if (report.reportedText) {
      sourceText = report.reportedText;
      textSource = "reporter_supplied_unverified";
    }

    if (!sourceText) {
      await snap.ref.update({
        status: "needs_manual_review",
        textSource,
        aiSkippedReason: report.chatType === "private" ? "e2e_no_text_shared" : "message_not_found",
        reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      return;
    }

    if (!(await reserveBudget(report.reporterId))) {
      await snap.ref.update({
        status: "needs_manual_review",
        textSource,
        aiSkippedReason: "daily_ai_budget_reached",
        reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      return;
    }

    const { text, truncated } = boundText(sourceText);
    try {
      const response = await anthropic().messages.create({
        model: MODEL,
        max_tokens: 300,
        system: SYSTEM_PROMPT,
        messages: [{ role: "user", content: buildUserPrompt(text, report.reasonCode, report.reportedUsername) }],
      });

      const raw = response.content.map((b) => (b.type === "text" ? b.text : "")).join("");
      // (string cast: older SDK typings predate the "refusal" stop reason)
      const verdict = (response.stop_reason as string) === "refusal" ? null : parseVerdict(raw);
      if (!verdict) {
        await snap.ref.update({
          status: "needs_manual_review",
          textSource,
          textTruncated: truncated,
          aiSkippedReason: "unparseable_ai_response",
          reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        return;
      }

      await snap.ref.update({
        status: "ai_reviewed",
        textSource,
        textTruncated: truncated,
        aiModel: MODEL,
        aiVerdict: verdict.verdict,
        aiSeverity: verdict.severity,
        aiCategory: verdict.category,
        aiExplanation: verdict.explanation,
        reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    } catch (error: unknown) {
      // Record only the error type — never echo request content into Firestore
      const errorType =
        error instanceof Anthropic.RateLimitError ? "rate_limited"
          : error instanceof Anthropic.APIConnectionError ? "connection_error"
            : error instanceof Anthropic.APIError ? `api_error_${error.status ?? "unknown"}`
              : "internal_error";
      functions.logger.error("AI review failed", { reportId: snap.id, errorType });
      await snap.ref.update({
        status: "ai_review_failed",
        textSource,
        aiError: errorType,
        reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    }
  });
