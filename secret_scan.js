// Pattern-based secret / personal-data scan. Prints file:line and pattern name only — never the
// matched text. Skips build outputs and node_modules and the two placeholder files written for the
// verification build (app/google-services.json, local.properties), which are not in the archive.
const fs = require('fs');
const path = require('path');
const root = process.argv[2];

const patterns = {
  'private key block': /-----BEGIN [A-Z ]*PRIVATE KEY-----/,
  'Google API key': /AIza[0-9A-Za-z_-]{35}/,
  'AWS access key': /AKIA[0-9A-Z]{16}/,
  'GitHub token': /gh[pousr]_[A-Za-z0-9]{36}/,
  'Slack token': /xox[abpr]-[A-Za-z0-9-]{10,}/,
  'Anthropic key': /sk-ant-[A-Za-z0-9_-]{20,}/,
  'generic sk- key': /\bsk-[A-Za-z0-9]{32,}/,
  'Stripe live key': /[rs]k_live_[A-Za-z0-9]{20,}/,
  'service-account JSON': /"type"\s*:\s*"service_account"/,
  'private_key JSON field': /"private_key"\s*:/,
  'password assignment': /(password|passwd|pwd)\s*[:=]\s*["']?[^\s"'${]{6,}/i,
  'secret/token assignment': /(secret|api[_-]?key|access[_-]?token)\s*[:=]\s*["'][A-Za-z0-9/+_=-]{16,}["']/i,
  'email address': /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/,
  'Windows user path': /[A-Za-z]:[\\/]Users[\\/][^\\/\s]+/i,
  '12-digit number (Firebase project number format)': /\b[0-9]{12}\b/,
};
const keyFileExt = /\.(jks|keystore|p12|pfx|pem|p8|key|der|crt)$/i;
const skipDirs = new Set(['build', 'node_modules', '.gradle', '.kotlin']);
let files = 0;
const hits = [];

(function walk(dir) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name);
    if (fs.statSync(p).isDirectory()) { if (!skipDirs.has(name)) walk(p); continue; }
    const rel = path.relative(root, p).split(path.sep).join('/');
    if (rel === 'app/google-services.json' || rel === 'local.properties' || rel === 'firestore-debug.log') continue;
    files++;
    if (keyFileExt.test(name)) hits.push(`${rel} | key/certificate file type`);
    const buf = fs.readFileSync(p);
    if (buf.includes(0)) continue; // binary
    buf.toString('utf8').split('\n').forEach((line, i) => {
      for (const [label, re] of Object.entries(patterns)) if (re.test(line)) hits.push(`${rel}:${i + 1} | ${label}`);
    });
  }
})(root);

console.log(`files scanned: ${files}`);
console.log(hits.length ? hits.join('\n') : 'no matches');
