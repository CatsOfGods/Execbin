package com.example.execbin

object PrivacyPolicy {

    fun get(lang: String): String = policies[lang] ?: policies["en"]!!

    private val policies = mapOf(
        "en" to """
PRIVACY POLICY — ExecBin Messenger
Last updated: March 22, 2026

1. INTRODUCTION
ExecBin ("we", "us", "our") is a privacy-focused encrypted messaging application. This Privacy Policy explains what information we collect, how we use it, and your rights regarding your data. By using ExecBin, you agree to this policy.

2. INFORMATION WE COLLECT

2.1 Account Information
• Email address — stored as a one-way cryptographic hash (SHA-256). We cannot read or recover your actual email. Firebase Authentication retains your email for login purposes.
• Username — a unique public identifier you choose during registration.
• Display name (nickname) — an optional name shown in chats.
• Profile photo — stored as Base64-encoded data. All EXIF metadata (GPS location, camera model, timestamps) is stripped before upload.
• Bio — an optional short text description.

2.2 Messages
• All private messages are end-to-end encrypted using ECDH P-256 key exchange with HKDF-SHA256 key derivation and AES-256-GCM encryption.
• Perfect Forward Secrecy (PFS) is implemented using per-message ephemeral key pairs — compromise of long-term keys cannot expose past messages.
• We cannot read, access, or decrypt your private messages. The encryption keys never leave your device.
• Global chat messages are not end-to-end encrypted and are visible to all users.
• Media attachments in private chats are encrypted before transmission. EXIF metadata is stripped from all images.

2.3 Device Information
• Device name (e.g. "Samsung Galaxy S24") — encrypted with AES-GCM before storage on our servers.
• Device identifier — a unique ID generated locally to manage active sessions.
• Login timestamps and last active times — encrypted before storage.
• This information is used solely for session management and security alerts (e.g. notifying you of new device logins).

2.4 Online Status
• Online/offline status — a boolean value indicating whether you are currently active.
• Last seen timestamp — records when you were last online. You can disable this in Settings > Privacy > "Hide Last Seen", which prevents other users from seeing your exact last activity time.

2.5 Security Data
• PIN hash — if you enable two-step verification, your PIN is hashed with PBKDF2 (100,000 iterations + random salt) before storage. We cannot recover your PIN.
• Login attempt counters — stored locally on your device for brute-force protection with exponential backoff.

2.6 Automatically Collected Data
• Firebase Authentication tokens for session management.
• Firebase Cloud Messaging (FCM) tokens for push notification delivery.
• We do NOT use any analytics, tracking, advertising SDKs, or third-party data collection tools.

3. HOW WE USE YOUR INFORMATION
We use your information exclusively for:
• Providing the messaging service (delivering messages, managing contacts).
• Account authentication and session management.
• Security features (device management, login alerts, brute-force protection).
• Delivering push notifications for new messages.
• Enforcing anti-spam protections (rate limiting, duplicate detection).
• Processing user reports for content moderation (keyword-based filtering only; no AI or human review of encrypted messages).

4. DATA STORAGE AND SECURITY
• Data is stored on Google Firebase (Firestore) servers.
• All connections use HTTPS with certificate pinning (GTS Root R1/R2/R3 and GlobalSign root certificates).
• Cleartext HTTP traffic is completely disabled.
• Firebase App Check with Play Integrity is enabled to prevent unauthorized API access.
• Sensitive metadata (device names, timestamps) is encrypted with AES-GCM before being stored on the server.
• Local sensitive data (encryption keys, PIN) is stored using Android EncryptedSharedPreferences backed by the Android Keystore.
• Key rotation occurs automatically every 30 days with backward compatibility for existing messages.

5. DATA SHARING
We do NOT sell, trade, rent, or share your personal data with ANY third parties. Your data is NEVER used for advertising.
• All private messages are end-to-end encrypted — we CANNOT read them even if we wanted to.
• We CANNOT provide message content to anyone, including law enforcement, because we do not have access to decryption keys.
• The only data stored on our servers is encrypted ciphertext that is unreadable without the user's private keys.

6. DATA RETENTION
• Messages are stored on Firebase servers until you delete them.
• Account data is retained until you delete your account.
• When you delete your account, all associated data (profile, messages, device records, security alerts) is permanently removed from our servers.
• Session data expires automatically after prolonged inactivity.

7. YOUR RIGHTS
You have the right to:
• Access your data — view your profile, devices, and settings at any time within the app.
• Correct your data — update your username, nickname, bio, and profile photo.
• Delete your data — permanently delete your account and all associated data from Settings.
• Control your privacy — hide your last seen status, enable PIN protection, manage trusted devices.
• Export — request a copy of your data by contacting us.

8. CHILDREN'S PRIVACY
ExecBin is not intended for children under 13 years of age. We do not knowingly collect personal information from children under 13. Users must confirm they are 13 or older during registration. If we learn that we have collected data from a child under 13, we will delete it promptly.

9. CHANGES TO THIS POLICY
We may update this Privacy Policy from time to time. Changes will be posted within the app. Continued use of ExecBin after changes constitutes acceptance of the updated policy.

10. CONTACT
If you have questions about this Privacy Policy or your data, contact us at:
Email: privacy@your-domain.com

11. TECHNICAL SECURITY SUMMARY
• End-to-end encryption: ECDH P-256 + HKDF-SHA256 + AES-256-GCM
• Perfect Forward Secrecy: per-message ephemeral EC key pairs
• Key rotation: automatic every 30 days
• PIN protection: PBKDF2 with 100,000 iterations
• Certificate pinning: GTS Root R1/R2/R3 + GlobalSign
• Metadata encryption: AES-GCM for device info and timestamps
• EXIF stripping: all image metadata removed before sending
• No analytics, no ads, no tracking
""".trimIndent(),

        "es" to """
POLÍTICA DE PRIVACIDAD — ExecBin Messenger
Última actualización: 22 de marzo de 2026

1. INTRODUCCIÓN
ExecBin ("nosotros", "nuestro") es una aplicación de mensajería cifrada centrada en la privacidad. Esta Política de Privacidad explica qué información recopilamos, cómo la usamos y sus derechos sobre sus datos. Al usar ExecBin, usted acepta esta política.

2. INFORMACIÓN QUE RECOPILAMOS

2.1 Información de la cuenta
• Dirección de correo electrónico — almacenada como un hash criptográfico unidireccional (SHA-256). No podemos leer ni recuperar su correo real. Firebase Authentication conserva su correo para el inicio de sesión.
• Nombre de usuario — un identificador público único que elige durante el registro.
• Nombre para mostrar (apodo) — un nombre opcional que se muestra en los chats.
• Foto de perfil — almacenada como datos codificados en Base64. Todos los metadatos EXIF (ubicación GPS, modelo de cámara, marcas de tiempo) se eliminan antes de la carga.
• Biografía — una descripción corta opcional.

2.2 Mensajes
• Todos los mensajes privados están cifrados de extremo a extremo usando intercambio de claves ECDH P-256 con derivación de claves HKDF-SHA256 y cifrado AES-256-GCM.
• Se implementa Perfect Forward Secrecy (PFS) usando pares de claves efímeras por mensaje — la compromisión de claves a largo plazo no puede exponer mensajes pasados.
• No podemos leer, acceder ni descifrar sus mensajes privados. Las claves de cifrado nunca salen de su dispositivo.
• Los mensajes del chat global no están cifrados de extremo a extremo y son visibles para todos los usuarios.
• Los archivos multimedia en chats privados se cifran antes de la transmisión. Los metadatos EXIF se eliminan de todas las imágenes.

2.3 Información del dispositivo
• Nombre del dispositivo — cifrado con AES-GCM antes del almacenamiento en nuestros servidores.
• Identificador del dispositivo — un ID único generado localmente para gestionar sesiones activas.
• Marcas de tiempo de inicio de sesión y última actividad — cifradas antes del almacenamiento.
• Esta información se utiliza únicamente para la gestión de sesiones y alertas de seguridad.

2.4 Estado en línea
• Estado en línea/desconectado — un valor booleano que indica si está activo actualmente.
• Marca de tiempo de última conexión — puede desactivarla en Ajustes > Privacidad > "Ocultar última conexión".

2.5 Datos de seguridad
• Hash del PIN — si activa la verificación en dos pasos, su PIN se hashea con PBKDF2 (100.000 iteraciones + sal aleatoria). No podemos recuperar su PIN.
• Contadores de intentos de inicio de sesión — almacenados localmente para protección contra fuerza bruta.

2.6 Datos recopilados automáticamente
• Tokens de autenticación de Firebase para gestión de sesiones.
• Tokens de Firebase Cloud Messaging (FCM) para entrega de notificaciones push.
• NO utilizamos análisis, seguimiento, publicidad ni herramientas de recopilación de datos de terceros.

3. CÓMO USAMOS SU INFORMACIÓN
Usamos su información exclusivamente para:
• Proporcionar el servicio de mensajería.
• Autenticación de cuenta y gestión de sesiones.
• Funciones de seguridad (gestión de dispositivos, alertas de inicio de sesión, protección contra fuerza bruta).
• Entrega de notificaciones push.
• Aplicar protecciones anti-spam.
• Procesar informes de usuarios para moderación de contenido.

4. ALMACENAMIENTO Y SEGURIDAD DE DATOS
• Los datos se almacenan en servidores de Google Firebase (Firestore).
• Todas las conexiones usan HTTPS con fijación de certificados (GTS Root R1/R2/R3 y certificados raíz GlobalSign).
• El tráfico HTTP sin cifrar está completamente desactivado.
• Firebase App Check con Play Integrity está habilitado.
• Los metadatos sensibles se cifran con AES-GCM antes del almacenamiento.
• Los datos sensibles locales se almacenan usando EncryptedSharedPreferences de Android.

5. COMPARTIR DATOS
NO vendemos, intercambiamos, alquilamos ni compartimos sus datos personales con NINGÚN tercero. Sus datos NUNCA se usan para publicidad.
• Todos los mensajes privados están cifrados de extremo a extremo — NO PODEMOS leerlos aunque quisiéramos.
• NO PODEMOS proporcionar el contenido de los mensajes a nadie, incluidas las fuerzas del orden, porque no tenemos acceso a las claves de descifrado.
• Los únicos datos almacenados en nuestros servidores son texto cifrado que es ilegible sin las claves privadas del usuario.

6. RETENCIÓN DE DATOS
• Los mensajes se almacenan hasta que usted los elimine.
• Los datos de la cuenta se conservan hasta que elimine su cuenta.
• Al eliminar su cuenta, todos los datos asociados se eliminan permanentemente.

7. SUS DERECHOS
Usted tiene derecho a:
• Acceder a sus datos en cualquier momento dentro de la aplicación.
• Corregir sus datos (nombre de usuario, apodo, biografía, foto).
• Eliminar permanentemente su cuenta y todos los datos asociados.
• Controlar su privacidad (ocultar última conexión, habilitar PIN, gestionar dispositivos).

8. PRIVACIDAD DE MENORES
ExecBin no está destinado a menores de 13 años. No recopilamos intencionalmente información de menores de 13 años. Los usuarios deben confirmar que tienen 13 años o más durante el registro.

9. CAMBIOS EN ESTA POLÍTICA
Podemos actualizar esta Política de Privacidad periódicamente. Los cambios se publicarán dentro de la aplicación.

10. CONTACTO
Email: privacy@your-domain.com

11. RESUMEN TÉCNICO DE SEGURIDAD
• Cifrado de extremo a extremo: ECDH P-256 + HKDF-SHA256 + AES-256-GCM
• Perfect Forward Secrecy: pares de claves efímeras por mensaje
• Rotación de claves: automática cada 30 días
• Protección PIN: PBKDF2 con 100.000 iteraciones
• Fijación de certificados: GTS Root R1/R2/R3 + GlobalSign
• Cifrado de metadatos: AES-GCM para información de dispositivos
• Eliminación EXIF: todos los metadatos de imágenes eliminados
• Sin análisis, sin publicidad, sin seguimiento
""".trimIndent(),

        "fr" to """
POLITIQUE DE CONFIDENTIALITÉ — ExecBin Messenger
Dernière mise à jour : 22 mars 2026

1. INTRODUCTION
ExecBin (« nous », « notre ») est une application de messagerie chiffrée axée sur la confidentialité. Cette Politique de Confidentialité explique quelles informations nous collectons, comment nous les utilisons et vos droits concernant vos données. En utilisant ExecBin, vous acceptez cette politique.

2. INFORMATIONS QUE NOUS COLLECTONS

2.1 Informations du compte
• Adresse e-mail — stockée sous forme de hachage cryptographique unidirectionnel (SHA-256). Nous ne pouvons ni lire ni récupérer votre e-mail réel. Firebase Authentication conserve votre e-mail pour la connexion.
• Nom d'utilisateur — un identifiant public unique choisi lors de l'inscription.
• Nom d'affichage (pseudo) — un nom optionnel affiché dans les conversations.
• Photo de profil — stockée en données encodées Base64. Toutes les métadonnées EXIF (localisation GPS, modèle d'appareil photo, horodatages) sont supprimées avant le téléchargement.
• Biographie — une courte description optionnelle.

2.2 Messages
• Tous les messages privés sont chiffrés de bout en bout avec l'échange de clés ECDH P-256, la dérivation HKDF-SHA256 et le chiffrement AES-256-GCM.
• Le Perfect Forward Secrecy (PFS) est implémenté avec des paires de clés éphémères par message — la compromission des clés à long terme ne peut pas exposer les messages passés.
• Nous ne pouvons ni lire, ni accéder, ni déchiffrer vos messages privés. Les clés de chiffrement ne quittent jamais votre appareil.
• Les messages du chat global ne sont pas chiffrés de bout en bout et sont visibles par tous les utilisateurs.
• Les pièces jointes multimédias des chats privés sont chiffrées avant transmission. Les métadonnées EXIF sont supprimées de toutes les images.

2.3 Informations sur l'appareil
• Nom de l'appareil — chiffré avec AES-GCM avant le stockage sur nos serveurs.
• Identifiant de l'appareil — un ID unique généré localement pour gérer les sessions actives.
• Horodatages de connexion et de dernière activité — chiffrés avant le stockage.

2.4 Statut en ligne
• Statut en ligne/hors ligne — une valeur booléenne indiquant votre activité.
• Horodatage de dernière connexion — désactivable dans Paramètres > Confidentialité > « Masquer dernière connexion ».

2.5 Données de sécurité
• Hash du PIN — haché avec PBKDF2 (100 000 itérations + sel aléatoire). Nous ne pouvons pas récupérer votre PIN.
• Compteurs de tentatives de connexion — stockés localement pour la protection contre la force brute.

2.6 Données collectées automatiquement
• Jetons d'authentification Firebase pour la gestion des sessions.
• Jetons Firebase Cloud Messaging (FCM) pour les notifications push.
• Nous n'utilisons AUCUN outil d'analyse, de suivi, de publicité ou de collecte de données tiers.

3. UTILISATION DE VOS INFORMATIONS
Nous utilisons vos informations exclusivement pour :
• Fournir le service de messagerie.
• L'authentification et la gestion des sessions.
• Les fonctionnalités de sécurité.
• La livraison des notifications push.
• Les protections anti-spam.
• Le traitement des signalements pour la modération.

4. STOCKAGE ET SÉCURITÉ DES DONNÉES
• Données stockées sur les serveurs Google Firebase (Firestore).
• Connexions HTTPS avec épinglage de certificats (GTS Root R1/R2/R3 et GlobalSign).
• Trafic HTTP non chiffré complètement désactivé.
• Firebase App Check avec Play Integrity activé.
• Métadonnées sensibles chiffrées avec AES-GCM.
• Données locales sensibles dans EncryptedSharedPreferences Android.

5. PARTAGE DE DONNÉES
Nous ne vendons, n'échangeons, ne louons ni ne partageons vos données personnelles avec AUCUN tiers. Vos données ne sont JAMAIS utilisées pour la publicité.
• Tous les messages privés sont chiffrés de bout en bout — nous NE POUVONS PAS les lire même si nous le voulions.
• Nous NE POUVONS PAS fournir le contenu des messages à quiconque, y compris les forces de l'ordre, car nous n'avons pas accès aux clés de déchiffrement.
• Les seules données stockées sur nos serveurs sont du texte chiffré illisible sans les clés privées de l'utilisateur.

6. CONSERVATION DES DONNÉES
• Les messages sont conservés jusqu'à leur suppression par vous.
• Les données du compte sont conservées jusqu'à la suppression du compte.
• La suppression du compte entraîne la suppression permanente de toutes les données associées.

7. VOS DROITS
Vous avez le droit de :
• Accéder à vos données à tout moment dans l'application.
• Corriger vos données.
• Supprimer définitivement votre compte et toutes les données associées.
• Contrôler votre confidentialité (masquer dernière connexion, activer le PIN, gérer les appareils).

8. CONFIDENTIALITÉ DES ENFANTS
ExecBin n'est pas destiné aux enfants de moins de 13 ans. Nous ne collectons pas sciemment d'informations auprès d'enfants de moins de 13 ans.

9. MODIFICATIONS DE CETTE POLITIQUE
Nous pouvons mettre à jour cette politique périodiquement. Les modifications seront publiées dans l'application.

10. CONTACT
Email : privacy@your-domain.com

11. RÉSUMÉ TECHNIQUE DE SÉCURITÉ
• Chiffrement de bout en bout : ECDH P-256 + HKDF-SHA256 + AES-256-GCM
• Perfect Forward Secrecy : paires de clés éphémères par message
• Rotation des clés : automatique tous les 30 jours
• Protection PIN : PBKDF2 avec 100 000 itérations
• Épinglage de certificats : GTS Root R1/R2/R3 + GlobalSign
• Chiffrement des métadonnées : AES-GCM
• Suppression EXIF : toutes les métadonnées d'images supprimées
• Aucune analyse, aucune publicité, aucun suivi
""".trimIndent(),

        "de" to """
DATENSCHUTZERKLÄRUNG — ExecBin Messenger
Letzte Aktualisierung: 22. März 2026

1. EINLEITUNG
ExecBin („wir", „uns", „unser") ist eine auf Datenschutz ausgerichtete verschlüsselte Messaging-Anwendung. Diese Datenschutzerklärung erläutert, welche Informationen wir erheben, wie wir sie verwenden und Ihre Rechte bezüglich Ihrer Daten. Durch die Nutzung von ExecBin stimmen Sie dieser Richtlinie zu.

2. INFORMATIONEN, DIE WIR ERHEBEN

2.1 Kontoinformationen
• E-Mail-Adresse — als kryptografischer Einweg-Hash (SHA-256) gespeichert. Wir können Ihre tatsächliche E-Mail weder lesen noch wiederherstellen. Firebase Authentication speichert Ihre E-Mail für Anmeldezwecke.
• Benutzername — eine eindeutige öffentliche Kennung, die Sie bei der Registrierung wählen.
• Anzeigename (Spitzname) — ein optionaler Name, der in Chats angezeigt wird.
• Profilfoto — als Base64-kodierte Daten gespeichert. Alle EXIF-Metadaten (GPS-Standort, Kameramodell, Zeitstempel) werden vor dem Hochladen entfernt.
• Biografie — eine optionale Kurzbeschreibung.

2.2 Nachrichten
• Alle privaten Nachrichten sind Ende-zu-Ende-verschlüsselt mit ECDH P-256 Schlüsselaustausch, HKDF-SHA256 Schlüsselableitung und AES-256-GCM Verschlüsselung.
• Perfect Forward Secrecy (PFS) wird mit pro-Nachricht ephemeren Schlüsselpaaren implementiert — die Kompromittierung langfristiger Schlüssel kann vergangene Nachrichten nicht offenlegen.
• Wir können Ihre privaten Nachrichten nicht lesen, darauf zugreifen oder sie entschlüsseln. Die Verschlüsselungsschlüssel verlassen niemals Ihr Gerät.
• Globale Chat-Nachrichten sind nicht Ende-zu-Ende-verschlüsselt und für alle Benutzer sichtbar.
• Medienanhänge in privaten Chats werden vor der Übertragung verschlüsselt. EXIF-Metadaten werden von allen Bildern entfernt.

2.3 Geräteinformationen
• Gerätename — mit AES-GCM verschlüsselt vor der Speicherung auf unseren Servern.
• Gerätekennung — eine lokal generierte eindeutige ID zur Verwaltung aktiver Sitzungen.
• Anmeldezeitstempel und letzte Aktivitätszeiten — vor der Speicherung verschlüsselt.

2.4 Online-Status
• Online-/Offline-Status — ein boolescher Wert.
• Zuletzt-gesehen-Zeitstempel — deaktivierbar unter Einstellungen > Datenschutz > „Zuletzt gesehen verbergen".

2.5 Sicherheitsdaten
• PIN-Hash — mit PBKDF2 gehasht (100.000 Iterationen + zufälliges Salt). Wir können Ihre PIN nicht wiederherstellen.
• Anmeldeversuchszähler — lokal auf Ihrem Gerät für Brute-Force-Schutz gespeichert.

2.6 Automatisch erhobene Daten
• Firebase-Authentifizierungstoken für die Sitzungsverwaltung.
• Firebase Cloud Messaging (FCM) Token für Push-Benachrichtigungen.
• Wir verwenden KEINE Analyse-, Tracking-, Werbe-SDKs oder Datenerhebungstools von Drittanbietern.

3. WIE WIR IHRE INFORMATIONEN VERWENDEN
Wir verwenden Ihre Informationen ausschließlich für:
• Bereitstellung des Messaging-Dienstes.
• Kontoauthentifizierung und Sitzungsverwaltung.
• Sicherheitsfunktionen.
• Zustellung von Push-Benachrichtigungen.
• Anti-Spam-Schutzmaßnahmen.
• Verarbeitung von Benutzerberichten zur Inhaltsmoderation.

4. DATENSPEICHERUNG UND SICHERHEIT
• Daten werden auf Google Firebase (Firestore) Servern gespeichert.
• Alle Verbindungen verwenden HTTPS mit Zertifikats-Pinning (GTS Root R1/R2/R3 und GlobalSign).
• Unverschlüsselter HTTP-Verkehr ist vollständig deaktiviert.
• Firebase App Check mit Play Integrity ist aktiviert.
• Sensible Metadaten werden mit AES-GCM verschlüsselt.
• Lokale sensible Daten werden mit Android EncryptedSharedPreferences gespeichert.

5. DATENWEITERGABE
Wir verkaufen, tauschen, vermieten oder teilen Ihre persönlichen Daten NICHT mit Dritten. Ihre Daten werden NIEMALS für Werbung verwendet.
• Alle privaten Nachrichten sind Ende-zu-Ende-verschlüsselt — wir KÖNNEN sie nicht lesen, selbst wenn wir es wollten.
• Wir KÖNNEN Nachrichteninhalte niemandem zur Verfügung stellen, einschließlich Strafverfolgungsbehörden, da wir keinen Zugang zu den Entschlüsselungsschlüsseln haben.
• Die einzigen auf unseren Servern gespeicherten Daten sind verschlüsselter Geheimtext, der ohne die privaten Schlüssel des Benutzers nicht lesbar ist.

6. DATENAUFBEWAHRUNG
• Nachrichten werden gespeichert, bis Sie sie löschen.
• Kontodaten werden bis zur Kontolöschung aufbewahrt.
• Bei Kontolöschung werden alle zugehörigen Daten dauerhaft entfernt.

7. IHRE RECHTE
Sie haben das Recht:
• Jederzeit auf Ihre Daten in der App zuzugreifen.
• Ihre Daten zu korrigieren.
• Ihr Konto und alle zugehörigen Daten dauerhaft zu löschen.
• Ihre Privatsphäre zu kontrollieren.

8. DATENSCHUTZ FÜR KINDER
ExecBin ist nicht für Kinder unter 13 Jahren bestimmt. Wir erheben wissentlich keine Daten von Kindern unter 13 Jahren.

9. ÄNDERUNGEN DIESER RICHTLINIE
Wir können diese Datenschutzerklärung gelegentlich aktualisieren. Änderungen werden in der App veröffentlicht.

10. KONTAKT
E-Mail: privacy@your-domain.com

11. TECHNISCHE SICHERHEITSÜBERSICHT
• Ende-zu-Ende-Verschlüsselung: ECDH P-256 + HKDF-SHA256 + AES-256-GCM
• Perfect Forward Secrecy: pro-Nachricht ephemere EC-Schlüsselpaare
• Schlüsselrotation: automatisch alle 30 Tage
• PIN-Schutz: PBKDF2 mit 100.000 Iterationen
• Zertifikats-Pinning: GTS Root R1/R2/R3 + GlobalSign
• Metadaten-Verschlüsselung: AES-GCM
• EXIF-Entfernung: alle Bild-Metadaten entfernt
• Keine Analyse, keine Werbung, kein Tracking
""".trimIndent(),

        "ar" to """
سياسة الخصوصية — ExecBin Messenger
آخر تحديث: 22 مارس 2026

1. المقدمة
ExecBin ("نحن"، "لنا") هو تطبيق مراسلة مشفر يركز على الخصوصية. توضح سياسة الخصوصية هذه المعلومات التي نجمعها، وكيف نستخدمها، وحقوقك فيما يتعلق ببياناتك. باستخدام ExecBin، فإنك توافق على هذه السياسة.

2. المعلومات التي نجمعها

2.1 معلومات الحساب
• عنوان البريد الإلكتروني — يُخزّن كتجزئة تشفيرية أحادية الاتجاه (SHA-256). لا يمكننا قراءة أو استرداد بريدك الإلكتروني الفعلي. يحتفظ Firebase Authentication ببريدك الإلكتروني لأغراض تسجيل الدخول.
• اسم المستخدم — معرف عام فريد تختاره أثناء التسجيل.
• الاسم المعروض (اللقب) — اسم اختياري يظهر في المحادثات.
• صورة الملف الشخصي — تُخزّن بترميز Base64. تُزال جميع بيانات EXIF الوصفية (موقع GPS، طراز الكاميرا، الطوابع الزمنية) قبل الرفع.
• النبذة — وصف قصير اختياري.

2.2 الرسائل
• جميع الرسائل الخاصة مشفرة من طرف إلى طرف باستخدام تبادل مفاتيح ECDH P-256 مع اشتقاق مفاتيح HKDF-SHA256 وتشفير AES-256-GCM.
• يتم تطبيق السرية التامة للأمام (PFS) باستخدام أزواج مفاتيح مؤقتة لكل رسالة — اختراق المفاتيح طويلة الأمد لا يمكن أن يكشف الرسائل السابقة.
• لا يمكننا قراءة أو الوصول إلى أو فك تشفير رسائلك الخاصة. مفاتيح التشفير لا تغادر جهازك أبداً.
• رسائل الدردشة العامة ليست مشفرة من طرف إلى طرف وهي مرئية لجميع المستخدمين.
• المرفقات الوسائطية في المحادثات الخاصة مشفرة قبل الإرسال. تُزال بيانات EXIF من جميع الصور.

2.3 معلومات الجهاز
• اسم الجهاز — مشفر بـ AES-GCM قبل التخزين على خوادمنا.
• معرف الجهاز — معرف فريد يُنشأ محلياً لإدارة الجلسات النشطة.
• طوابع زمنية لتسجيل الدخول وآخر نشاط — مشفرة قبل التخزين.

2.4 حالة الاتصال
• حالة متصل/غير متصل — قيمة منطقية تشير إلى نشاطك الحالي.
• طابع زمني لآخر ظهور — يمكن تعطيله في الإعدادات > الخصوصية > "إخفاء آخر ظهور".

2.5 بيانات الأمان
• تجزئة رمز PIN — تُجزأ بـ PBKDF2 (100,000 تكرار + ملح عشوائي). لا يمكننا استرداد رمز PIN الخاص بك.
• عدادات محاولات تسجيل الدخول — تُخزّن محلياً للحماية من هجمات القوة الغاشمة.

2.6 البيانات المجمعة تلقائياً
• رموز مصادقة Firebase لإدارة الجلسات.
• رموز Firebase Cloud Messaging (FCM) لإرسال الإشعارات.
• لا نستخدم أي أدوات تحليل أو تتبع أو إعلانات أو جمع بيانات من أطراف ثالثة.

3. كيف نستخدم معلوماتك
نستخدم معلوماتك حصرياً لـ:
• تقديم خدمة المراسلة.
• مصادقة الحساب وإدارة الجلسات.
• ميزات الأمان.
• إرسال الإشعارات.
• تطبيق حماية مكافحة البريد العشوائي.
• معالجة تقارير المستخدمين للإشراف على المحتوى.

4. تخزين البيانات والأمان
• تُخزّن البيانات على خوادم Google Firebase (Firestore).
• جميع الاتصالات تستخدم HTTPS مع تثبيت الشهادات.
• حركة HTTP غير المشفرة معطلة تماماً.
• Firebase App Check مع Play Integrity مفعّل.
• البيانات الوصفية الحساسة مشفرة بـ AES-GCM.
• البيانات المحلية الحساسة في EncryptedSharedPreferences.

5. مشاركة البيانات
لا نبيع أو نتاجر أو نؤجر أو نشارك بياناتك الشخصية مع أي أطراف ثالثة على الإطلاق. بياناتك لا تُستخدم أبداً للإعلانات.
• جميع الرسائل الخاصة مشفرة من طرف إلى طرف — لا يمكننا قراءتها حتى لو أردنا ذلك.
• لا يمكننا تقديم محتوى الرسائل لأي شخص، بما في ذلك جهات إنفاذ القانون، لأننا لا نملك الوصول إلى مفاتيح فك التشفير.
• البيانات الوحيدة المخزنة على خوادمنا هي نصوص مشفرة لا يمكن قراءتها بدون المفاتيح الخاصة للمستخدم.

6. الاحتفاظ بالبيانات
• تُخزّن الرسائل حتى تحذفها.
• تُحتفظ ببيانات الحساب حتى تحذف حسابك.
• عند حذف حسابك، تُزال جميع البيانات المرتبطة نهائياً.

7. حقوقك
لديك الحق في:
• الوصول إلى بياناتك في أي وقت داخل التطبيق.
• تصحيح بياناتك.
• حذف حسابك وجميع البيانات المرتبطة نهائياً.
• التحكم في خصوصيتك.

8. خصوصية الأطفال
ExecBin غير مخصص للأطفال دون 13 عاماً. لا نجمع عن قصد معلومات من أطفال دون 13 عاماً.

9. التغييرات على هذه السياسة
قد نحدّث سياسة الخصوصية هذه من وقت لآخر. ستُنشر التغييرات داخل التطبيق.

10. الاتصال
البريد الإلكتروني: privacy@your-domain.com

11. ملخص الأمان التقني
• تشفير من طرف إلى طرف: ECDH P-256 + HKDF-SHA256 + AES-256-GCM
• السرية التامة للأمام: أزواج مفاتيح EC مؤقتة لكل رسالة
• تدوير المفاتيح: تلقائي كل 30 يوماً
• حماية PIN: PBKDF2 بـ 100,000 تكرار
• تثبيت الشهادات: GTS Root R1/R2/R3 + GlobalSign
• تشفير البيانات الوصفية: AES-GCM
• إزالة EXIF: جميع بيانات الصور الوصفية تُزال
• بدون تحليلات، بدون إعلانات، بدون تتبع
""".trimIndent(),

        "ru" to """
ПОЛИТИКА КОНФИДЕНЦИАЛЬНОСТИ — ExecBin Messenger
Последнее обновление: 22 марта 2026 г.

1. ВВЕДЕНИЕ
ExecBin («мы», «нас», «наш») — это приложение для зашифрованного обмена сообщениями, ориентированное на конфиденциальность. Настоящая Политика конфиденциальности объясняет, какую информацию мы собираем, как мы её используем и ваши права в отношении ваших данных. Используя ExecBin, вы соглашаетесь с этой политикой.

2. ИНФОРМАЦИЯ, КОТОРУЮ МЫ СОБИРАЕМ

2.1 Информация об аккаунте
• Адрес электронной почты — хранится как односторонний криптографический хеш (SHA-256). Мы не можем прочитать или восстановить вашу реальную почту. Firebase Authentication хранит вашу почту для входа в систему.
• Имя пользователя — уникальный публичный идентификатор, который вы выбираете при регистрации.
• Отображаемое имя (никнейм) — необязательное имя, отображаемое в чатах.
• Фото профиля — хранится в формате Base64. Все метаданные EXIF (GPS-координаты, модель камеры, временные метки) удаляются перед загрузкой.
• Биография — необязательное краткое описание.

2.2 Сообщения
• Все личные сообщения зашифрованы сквозным шифрованием с использованием обмена ключами ECDH P-256, деривации ключей HKDF-SHA256 и шифрования AES-256-GCM.
• Реализована совершенная прямая секретность (PFS) с использованием эфемерных пар ключей для каждого сообщения — компрометация долгосрочных ключей не может раскрыть прошлые сообщения.
• Мы не можем читать, получать доступ или расшифровывать ваши личные сообщения. Ключи шифрования никогда не покидают ваше устройство.
• Сообщения глобального чата не зашифрованы сквозным шифрованием и видны всем пользователям.
• Медиавложения в личных чатах шифруются перед передачей. Метаданные EXIF удаляются из всех изображений.

2.3 Информация об устройстве
• Название устройства — зашифровано AES-GCM перед хранением на наших серверах.
• Идентификатор устройства — уникальный ID, генерируемый локально для управления активными сессиями.
• Временные метки входа и последней активности — зашифрованы перед хранением.

2.4 Статус онлайн
• Статус онлайн/офлайн — логическое значение, указывающее на вашу активность.
• Временная метка последнего входа — можно отключить в Настройки > Конфиденциальность > «Скрыть последний вход».

2.5 Данные безопасности
• Хеш PIN-кода — хешируется PBKDF2 (100 000 итераций + случайная соль). Мы не можем восстановить ваш PIN-код.
• Счётчики попыток входа — хранятся локально для защиты от подбора.

2.6 Автоматически собираемые данные
• Токены аутентификации Firebase для управления сессиями.
• Токены Firebase Cloud Messaging (FCM) для доставки push-уведомлений.
• Мы НЕ используем аналитику, отслеживание, рекламные SDK или сторонние инструменты сбора данных.

3. КАК МЫ ИСПОЛЬЗУЕМ ВАШУ ИНФОРМАЦИЮ
Мы используем вашу информацию исключительно для:
• Предоставления сервиса обмена сообщениями.
• Аутентификации аккаунта и управления сессиями.
• Функций безопасности.
• Доставки push-уведомлений.
• Применения защиты от спама.
• Обработки жалоб пользователей для модерации контента.

4. ХРАНЕНИЕ И БЕЗОПАСНОСТЬ ДАННЫХ
• Данные хранятся на серверах Google Firebase (Firestore).
• Все соединения используют HTTPS с закреплением сертификатов (GTS Root R1/R2/R3 и GlobalSign).
• Незашифрованный HTTP-трафик полностью отключён.
• Firebase App Check с Play Integrity активирован.
• Конфиденциальные метаданные зашифрованы AES-GCM.
• Локальные конфиденциальные данные хранятся в Android EncryptedSharedPreferences.

5. ПЕРЕДАЧА ДАННЫХ
Мы НЕ продаём, не обмениваем, не сдаём в аренду и не передаём ваши персональные данные НИКАКИМ третьим лицам. Ваши данные НИКОГДА не используются для рекламы.
• Все личные сообщения зашифрованы сквозным шифрованием — мы НЕ МОЖЕМ их прочитать, даже если бы захотели.
• Мы НЕ МОЖЕМ предоставить содержимое сообщений кому-либо, включая правоохранительные органы, поскольку у нас нет доступа к ключам дешифрования.
• Единственные данные, хранящиеся на наших серверах — это зашифрованный текст, который невозможно прочитать без закрытых ключей пользователя.

6. СРОК ХРАНЕНИЯ ДАННЫХ
• Сообщения хранятся до тех пор, пока вы их не удалите.
• Данные аккаунта хранятся до удаления аккаунта.
• При удалении аккаунта все связанные данные удаляются безвозвратно.

7. ВАШИ ПРАВА
Вы имеете право:
• Получать доступ к своим данным в любое время в приложении.
• Исправлять свои данные.
• Безвозвратно удалить свой аккаунт и все связанные данные.
• Контролировать свою конфиденциальность.

8. КОНФИДЕНЦИАЛЬНОСТЬ ДЕТЕЙ
ExecBin не предназначен для детей младше 13 лет. Мы сознательно не собираем информацию от детей младше 13 лет.

9. ИЗМЕНЕНИЯ ЭТОЙ ПОЛИТИКИ
Мы можем периодически обновлять эту Политику конфиденциальности. Изменения будут опубликованы в приложении.

10. КОНТАКТЫ
Email: privacy@your-domain.com

11. ТЕХНИЧЕСКАЯ СВОДКА ПО БЕЗОПАСНОСТИ
• Сквозное шифрование: ECDH P-256 + HKDF-SHA256 + AES-256-GCM
• Совершенная прямая секретность: эфемерные пары ключей EC для каждого сообщения
• Ротация ключей: автоматически каждые 30 дней
• Защита PIN: PBKDF2 со 100 000 итераций
• Закрепление сертификатов: GTS Root R1/R2/R3 + GlobalSign
• Шифрование метаданных: AES-GCM
• Удаление EXIF: все метаданные изображений удалены
• Без аналитики, без рекламы, без отслеживания
""".trimIndent(),

        "pt" to """
POLÍTICA DE PRIVACIDADE — ExecBin Messenger
Última atualização: 22 de março de 2026

1. INTRODUÇÃO
ExecBin ("nós", "nosso") é um aplicativo de mensagens criptografadas focado em privacidade. Esta Política de Privacidade explica quais informações coletamos, como as usamos e seus direitos em relação aos seus dados. Ao usar o ExecBin, você concorda com esta política.

2. INFORMAÇÕES QUE COLETAMOS

2.1 Informações da conta
• Endereço de e-mail — armazenado como hash criptográfico unidirecional (SHA-256). Não podemos ler ou recuperar seu e-mail real. O Firebase Authentication mantém seu e-mail para login.
• Nome de usuário — um identificador público único escolhido durante o registro.
• Nome de exibição (apelido) — um nome opcional exibido nos chats.
• Foto de perfil — armazenada como dados codificados em Base64. Todos os metadados EXIF (localização GPS, modelo da câmera, carimbos de data/hora) são removidos antes do upload.
• Biografia — uma descrição curta opcional.

2.2 Mensagens
• Todas as mensagens privadas são criptografadas de ponta a ponta usando troca de chaves ECDH P-256 com derivação de chaves HKDF-SHA256 e criptografia AES-256-GCM.
• Perfect Forward Secrecy (PFS) é implementado usando pares de chaves efêmeras por mensagem — o comprometimento de chaves de longo prazo não pode expor mensagens passadas.
• Não podemos ler, acessar ou descriptografar suas mensagens privadas. As chaves de criptografia nunca saem do seu dispositivo.
• Mensagens do chat global não são criptografadas de ponta a ponta e são visíveis para todos os usuários.
• Anexos de mídia em chats privados são criptografados antes da transmissão. Metadados EXIF são removidos de todas as imagens.

2.3 Informações do dispositivo
• Nome do dispositivo — criptografado com AES-GCM antes do armazenamento em nossos servidores.
• Identificador do dispositivo — um ID único gerado localmente para gerenciar sessões ativas.
• Carimbos de data/hora de login e última atividade — criptografados antes do armazenamento.

2.4 Status online
• Status online/offline — um valor booleano indicando sua atividade atual.
• Carimbo de data/hora de última visualização — desativável em Configurações > Privacidade > "Ocultar última visualização".

2.5 Dados de segurança
• Hash do PIN — hasheado com PBKDF2 (100.000 iterações + salt aleatório). Não podemos recuperar seu PIN.
• Contadores de tentativas de login — armazenados localmente para proteção contra força bruta.

2.6 Dados coletados automaticamente
• Tokens de autenticação Firebase para gerenciamento de sessões.
• Tokens Firebase Cloud Messaging (FCM) para notificações push.
• NÃO utilizamos ferramentas de análise, rastreamento, publicidade ou coleta de dados de terceiros.

3. COMO USAMOS SUAS INFORMAÇÕES
Usamos suas informações exclusivamente para:
• Fornecer o serviço de mensagens.
• Autenticação de conta e gerenciamento de sessões.
• Recursos de segurança.
• Entrega de notificações push.
• Proteções anti-spam.
• Processamento de denúncias para moderação de conteúdo.

4. ARMAZENAMENTO E SEGURANÇA DE DADOS
• Dados armazenados em servidores Google Firebase (Firestore).
• Todas as conexões usam HTTPS com pinning de certificados.
• Tráfego HTTP não criptografado completamente desabilitado.
• Firebase App Check com Play Integrity habilitado.
• Metadados sensíveis criptografados com AES-GCM.
• Dados locais sensíveis em EncryptedSharedPreferences do Android.

5. COMPARTILHAMENTO DE DADOS
NÃO vendemos, trocamos, alugamos ou compartilhamos seus dados pessoais com NENHUM terceiro. Seus dados NUNCA são usados para publicidade.
• Todas as mensagens privadas são criptografadas de ponta a ponta — NÃO PODEMOS lê-las mesmo que quiséssemos.
• NÃO PODEMOS fornecer o conteúdo das mensagens a ninguém, incluindo autoridades policiais, porque não temos acesso às chaves de descriptografia.
• Os únicos dados armazenados em nossos servidores são texto cifrado que é ilegível sem as chaves privadas do usuário.

6. RETENÇÃO DE DADOS
• Mensagens são armazenadas até você excluí-las.
• Dados da conta são mantidos até a exclusão da conta.
• Ao excluir sua conta, todos os dados associados são removidos permanentemente.

7. SEUS DIREITOS
Você tem o direito de:
• Acessar seus dados a qualquer momento no aplicativo.
• Corrigir seus dados.
• Excluir permanentemente sua conta e todos os dados associados.
• Controlar sua privacidade.

8. PRIVACIDADE DE CRIANÇAS
ExecBin não é destinado a crianças menores de 13 anos. Não coletamos intencionalmente informações de crianças menores de 13 anos.

9. ALTERAÇÕES NESTA POLÍTICA
Podemos atualizar esta Política de Privacidade periodicamente. Alterações serão publicadas no aplicativo.

10. CONTATO
Email: privacy@your-domain.com

11. RESUMO TÉCNICO DE SEGURANÇA
• Criptografia de ponta a ponta: ECDH P-256 + HKDF-SHA256 + AES-256-GCM
• Perfect Forward Secrecy: pares de chaves EC efêmeras por mensagem
• Rotação de chaves: automática a cada 30 dias
• Proteção PIN: PBKDF2 com 100.000 iterações
• Pinning de certificados: GTS Root R1/R2/R3 + GlobalSign
• Criptografia de metadados: AES-GCM
• Remoção EXIF: todos os metadados de imagens removidos
• Sem análise, sem publicidade, sem rastreamento
""".trimIndent(),

        "it" to """
INFORMATIVA SULLA PRIVACY — ExecBin Messenger
Ultimo aggiornamento: 22 marzo 2026

1. INTRODUZIONE
ExecBin ("noi", "nostro") è un'applicazione di messaggistica crittografata incentrata sulla privacy. Questa Informativa sulla Privacy spiega quali informazioni raccogliamo, come le utilizziamo e i tuoi diritti riguardo ai tuoi dati. Utilizzando ExecBin, accetti questa politica.

2. INFORMAZIONI CHE RACCOGLIAMO

2.1 Informazioni dell'account
• Indirizzo email — memorizzato come hash crittografico unidirezionale (SHA-256). Non possiamo leggere o recuperare la tua email reale. Firebase Authentication conserva la tua email per l'accesso.
• Nome utente — un identificatore pubblico unico scelto durante la registrazione.
• Nome visualizzato (soprannome) — un nome opzionale mostrato nelle chat.
• Foto del profilo — memorizzata come dati codificati Base64. Tutti i metadati EXIF (posizione GPS, modello fotocamera, timestamp) vengono rimossi prima del caricamento.
• Biografia — una breve descrizione opzionale.

2.2 Messaggi
• Tutti i messaggi privati sono crittografati end-to-end utilizzando lo scambio di chiavi ECDH P-256 con derivazione delle chiavi HKDF-SHA256 e crittografia AES-256-GCM.
• Il Perfect Forward Secrecy (PFS) è implementato con coppie di chiavi effimere per messaggio — la compromissione delle chiavi a lungo termine non può esporre i messaggi passati.
• Non possiamo leggere, accedere o decrittare i tuoi messaggi privati. Le chiavi di crittografia non lasciano mai il tuo dispositivo.
• I messaggi della chat globale non sono crittografati end-to-end e sono visibili a tutti gli utenti.
• Gli allegati multimediali nelle chat private sono crittografati prima della trasmissione. I metadati EXIF vengono rimossi da tutte le immagini.

2.3 Informazioni sul dispositivo
• Nome del dispositivo — crittografato con AES-GCM prima dell'archiviazione sui nostri server.
• Identificativo del dispositivo — un ID unico generato localmente per gestire le sessioni attive.
• Timestamp di accesso e ultima attività — crittografati prima dell'archiviazione.

2.4 Stato online
• Stato online/offline — un valore booleano che indica la tua attività.
• Timestamp dell'ultimo accesso — disattivabile in Impostazioni > Privacy > "Nascondi ultimo accesso".

2.5 Dati di sicurezza
• Hash del PIN — elaborato con PBKDF2 (100.000 iterazioni + sale casuale). Non possiamo recuperare il tuo PIN.
• Contatori dei tentativi di accesso — memorizzati localmente per la protezione da attacchi brute force.

2.6 Dati raccolti automaticamente
• Token di autenticazione Firebase per la gestione delle sessioni.
• Token Firebase Cloud Messaging (FCM) per le notifiche push.
• NON utilizziamo strumenti di analisi, tracciamento, pubblicità o raccolta dati di terze parti.

3. COME UTILIZZIAMO LE TUE INFORMAZIONI
Utilizziamo le tue informazioni esclusivamente per:
• Fornire il servizio di messaggistica.
• Autenticazione dell'account e gestione delle sessioni.
• Funzionalità di sicurezza.
• Consegna delle notifiche push.
• Protezioni anti-spam.
• Elaborazione delle segnalazioni per la moderazione dei contenuti.

4. ARCHIVIAZIONE E SICUREZZA DEI DATI
• Dati archiviati su server Google Firebase (Firestore).
• Tutte le connessioni utilizzano HTTPS con certificate pinning.
• Traffico HTTP non crittografato completamente disabilitato.
• Firebase App Check con Play Integrity abilitato.
• Metadati sensibili crittografati con AES-GCM.
• Dati locali sensibili in EncryptedSharedPreferences di Android.

5. CONDIVISIONE DEI DATI
NON vendiamo, scambiamo, affittiamo o condividiamo i tuoi dati personali con NESSUNA terza parte. I tuoi dati non vengono MAI utilizzati per la pubblicità.
• Tutti i messaggi privati sono crittografati end-to-end — NON POSSIAMO leggerli nemmeno se lo volessimo.
• NON POSSIAMO fornire il contenuto dei messaggi a nessuno, comprese le forze dell'ordine, perché non abbiamo accesso alle chiavi di decrittazione.
• Gli unici dati memorizzati sui nostri server sono testo cifrato illeggibile senza le chiavi private dell'utente.

6. CONSERVAZIONE DEI DATI
• I messaggi vengono conservati fino alla loro eliminazione.
• I dati dell'account vengono conservati fino all'eliminazione dell'account.
• L'eliminazione dell'account comporta la rimozione permanente di tutti i dati associati.

7. I TUOI DIRITTI
Hai il diritto di:
• Accedere ai tuoi dati in qualsiasi momento nell'applicazione.
• Correggere i tuoi dati.
• Eliminare permanentemente il tuo account e tutti i dati associati.
• Controllare la tua privacy.

8. PRIVACY DEI MINORI
ExecBin non è destinato a minori di 13 anni. Non raccogliamo consapevolmente informazioni da minori di 13 anni.

9. MODIFICHE A QUESTA POLITICA
Potremmo aggiornare questa Informativa sulla Privacy periodicamente. Le modifiche saranno pubblicate nell'applicazione.

10. CONTATTO
Email: privacy@your-domain.com

11. RIEPILOGO TECNICO DELLA SICUREZZA
• Crittografia end-to-end: ECDH P-256 + HKDF-SHA256 + AES-256-GCM
• Perfect Forward Secrecy: coppie di chiavi EC effimere per messaggio
• Rotazione delle chiavi: automatica ogni 30 giorni
• Protezione PIN: PBKDF2 con 100.000 iterazioni
• Certificate pinning: GTS Root R1/R2/R3 + GlobalSign
• Crittografia dei metadati: AES-GCM
• Rimozione EXIF: tutti i metadati delle immagini rimossi
• Nessuna analisi, nessuna pubblicità, nessun tracciamento
""".trimIndent(),

        "zh" to """
隐私政策 — ExecBin Messenger
最后更新：2026年3月22日

1. 简介
ExecBin（"我们"）是一款注重隐私的加密通讯应用程序。本隐私政策说明我们收集哪些信息、如何使用这些信息以及您对数据的权利。使用ExecBin即表示您同意本政策。

2. 我们收集的信息

2.1 账户信息
• 电子邮件地址 — 以单向加密哈希（SHA-256）形式存储。我们无法读取或恢复您的实际电子邮件。Firebase Authentication保留您的电子邮件用于登录。
• 用户名 — 您在注册时选择的唯一公共标识符。
• 显示名称（昵称） — 在聊天中显示的可选名称。
• 头像 — 以Base64编码数据存储。所有EXIF元数据（GPS位置、相机型号、时间戳）在上传前被移除。
• 个人简介 — 可选的简短描述。

2.2 消息
• 所有私人消息均使用ECDH P-256密钥交换、HKDF-SHA256密钥派生和AES-256-GCM加密进行端到端加密。
• 通过每条消息的临时密钥对实现完美前向保密（PFS）——长期密钥的泄露不会暴露过去的消息。
• 我们无法读取、访问或解密您的私人消息。加密密钥永远不会离开您的设备。
• 全局聊天消息不进行端到端加密，所有用户可见。
• 私人聊天中的媒体附件在传输前加密。所有图像的EXIF元数据均被移除。

2.3 设备信息
• 设备名称 — 在存储到我们的服务器之前使用AES-GCM加密。
• 设备标识符 — 本地生成的唯一ID，用于管理活动会话。
• 登录时间戳和最后活动时间 — 存储前加密。

2.4 在线状态
• 在线/离线状态 — 表示您当前是否活跃的布尔值。
• 最后在线时间戳 — 可在设置 > 隐私 > "隐藏最后在线时间"中禁用。

2.5 安全数据
• PIN哈希 — 使用PBKDF2（100,000次迭代+随机盐）进行哈希。我们无法恢复您的PIN。
• 登录尝试计数器 — 本地存储用于暴力破解保护。

2.6 自动收集的数据
• Firebase身份验证令牌用于会话管理。
• Firebase Cloud Messaging（FCM）令牌用于推送通知。
• 我们不使用任何分析、跟踪、广告SDK或第三方数据收集工具。

3. 我们如何使用您的信息
我们仅将您的信息用于：
• 提供消息服务。
• 账户认证和会话管理。
• 安全功能。
• 推送通知。
• 反垃圾邮件保护。
• 处理用户举报以进行内容审核。

4. 数据存储与安全
• 数据存储在Google Firebase（Firestore）服务器上。
• 所有连接使用HTTPS并进行证书固定（GTS Root R1/R2/R3和GlobalSign）。
• 完全禁用明文HTTP流量。
• 启用Firebase App Check与Play Integrity。
• 敏感元数据使用AES-GCM加密。
• 本地敏感数据存储在Android EncryptedSharedPreferences中。

5. 数据共享
我们不会向任何第三方出售、交易、出租或共享您的个人数据。您的数据绝不会用于广告。
• 所有私人消息均已端到端加密——即使我们想要读取也无法做到。
• 我们无法向任何人（包括执法机构）提供消息内容，因为我们没有解密密钥的访问权限。
• 我们服务器上存储的唯一数据是加密密文，没有用户的私钥则无法读取。

6. 数据保留
• 消息存储直到您删除它们。
• 账户数据保留直到您删除账户。
• 删除账户时，所有相关数据将被永久删除。

7. 您的权利
您有权：
• 随时在应用内访问您的数据。
• 更正您的数据。
• 永久删除您的账户和所有相关数据。
• 控制您的隐私。

8. 儿童隐私
ExecBin不面向13岁以下的儿童。我们不会故意收集13岁以下儿童的信息。

9. 政策变更
我们可能会不时更新本隐私政策。变更将在应用内发布。

10. 联系方式
电子邮件：privacy@your-domain.com

11. 技术安全摘要
• 端到端加密：ECDH P-256 + HKDF-SHA256 + AES-256-GCM
• 完美前向保密：每条消息的临时EC密钥对
• 密钥轮换：每30天自动
• PIN保护：PBKDF2 100,000次迭代
• 证书固定：GTS Root R1/R2/R3 + GlobalSign
• 元数据加密：AES-GCM
• EXIF移除：所有图像元数据被移除
• 无分析、无广告、无跟踪
""".trimIndent(),

        "ja" to """
プライバシーポリシー — ExecBin Messenger
最終更新：2026年3月22日

1. はじめに
ExecBin（「私たち」「当社」）は、プライバシーを重視した暗号化メッセージングアプリケーションです。このプライバシーポリシーは、収集する情報、その使用方法、およびお客様のデータに関する権利を説明します。ExecBinを使用することにより、このポリシーに同意したものとみなされます。

2. 収集する情報

2.1 アカウント情報
• メールアドレス — 一方向暗号ハッシュ（SHA-256）として保存されます。実際のメールアドレスを読み取ったり復元したりすることはできません。Firebase Authenticationがログイン用にメールを保持します。
• ユーザー名 — 登録時に選択する一意の公開識別子。
• 表示名（ニックネーム） — チャットで表示されるオプションの名前。
• プロフィール写真 — Base64エンコードデータとして保存。すべてのEXIFメタデータ（GPS位置情報、カメラモデル、タイムスタンプ）はアップロード前に削除されます。
• 自己紹介 — オプションの短い説明文。

2.2 メッセージ
• すべてのプライベートメッセージは、ECDH P-256鍵交換、HKDF-SHA256鍵導出、AES-256-GCM暗号化を使用してエンドツーエンド暗号化されています。
• メッセージごとの一時鍵ペアを使用したPerfect Forward Secrecy（PFS）が実装されています。長期鍵の漏洩によって過去のメッセージが公開されることはありません。
• プライベートメッセージの読み取り、アクセス、復号化は一切できません。暗号化鍵はお客様のデバイスから離れることはありません。
• グローバルチャットメッセージはエンドツーエンド暗号化されておらず、すべてのユーザーに表示されます。
• プライベートチャットのメディア添付ファイルは送信前に暗号化されます。すべての画像からEXIFメタデータが削除されます。

2.3 デバイス情報
• デバイス名 — サーバーに保存する前にAES-GCMで暗号化されます。
• デバイス識別子 — アクティブセッションを管理するためにローカルで生成される一意のID。
• ログインタイムスタンプと最終アクティブ時間 — 保存前に暗号化されます。

2.4 オンラインステータス
• オンライン/オフラインステータス — 現在アクティブかどうかを示すブール値。
• 最終閲覧タイムスタンプ — 設定 > プライバシー > 「最終閲覧を非表示」で無効にできます。

2.5 セキュリティデータ
• PINハッシュ — PBKDF2（100,000回反復+ランダムソルト）でハッシュ化。PINを復元することはできません。
• ログイン試行カウンター — ブルートフォース保護のためにローカルに保存されます。

2.6 自動的に収集されるデータ
• セッション管理用のFirebase認証トークン。
• プッシュ通知配信用のFirebase Cloud Messaging（FCM）トークン。
• 分析、トラッキング、広告SDK、サードパーティデータ収集ツールは一切使用しません。

3. 情報の使用方法
お客様の情報は以下の目的のみに使用します：
• メッセージングサービスの提供。
• アカウント認証とセッション管理。
• セキュリティ機能。
• プッシュ通知の配信。
• スパム対策の適用。
• コンテンツモデレーションのためのユーザー報告の処理。

4. データの保存とセキュリティ
• データはGoogle Firebase（Firestore）サーバーに保存されます。
• すべての接続は証明書ピニング付きHTTPSを使用（GTS Root R1/R2/R3およびGlobalSign）。
• 平文HTTPトラフィックは完全に無効化。
• Firebase App CheckとPlay Integrityが有効。
• 機密メタデータはAES-GCMで暗号化。
• ローカルの機密データはAndroid EncryptedSharedPreferencesに保存。

5. データの共有
お客様の個人データをいかなる第三者にも販売、交換、賃貸、共有することは一切ありません。お客様のデータが広告に使用されることは絶対にありません。
• すべてのプライベートメッセージはエンドツーエンド暗号化されており、たとえ私たちが読みたくても読むことはできません。
• 私たちは復号鍵へのアクセス権を持たないため、法執行機関を含むいかなる者にもメッセージの内容を提供することはできません。
• 当社のサーバーに保存されているデータは、ユーザーの秘密鍵なしでは読み取れない暗号化されたテキストのみです。

6. データの保持
• メッセージは削除するまで保存されます。
• アカウントデータはアカウント削除まで保持されます。
• アカウント削除時、すべての関連データが永久に削除されます。

7. お客様の権利
お客様には以下の権利があります：
• アプリ内でいつでもデータにアクセスする。
• データを修正する。
• アカウントとすべての関連データを完全に削除する。
• プライバシーを制御する。

8. 子供のプライバシー
ExecBinは13歳未満の子供を対象としていません。13歳未満の子供から意図的に情報を収集することはありません。

9. ポリシーの変更
このプライバシーポリシーは随時更新される場合があります。変更はアプリ内で公開されます。

10. お問い合わせ
メール：privacy@your-domain.com

11. テクニカルセキュリティサマリー
• エンドツーエンド暗号化：ECDH P-256 + HKDF-SHA256 + AES-256-GCM
• Perfect Forward Secrecy：メッセージごとの一時EC鍵ペア
• 鍵のローテーション：30日ごとに自動
• PIN保護：PBKDF2 100,000回反復
• 証明書ピニング：GTS Root R1/R2/R3 + GlobalSign
• メタデータ暗号化：AES-GCM
• EXIF除去：すべての画像メタデータを除去
• 分析なし、広告なし、トラッキングなし
""".trimIndent()
    )
}
