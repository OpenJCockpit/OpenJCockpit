<#--
  Metafactory Keycloak login theme.
  This page keeps the standard Keycloak username/password login flow but
  replaces the default visual layer with an Arwes-inspired futuristic UI.
-->
<#assign usernameLabel>
  <#if !realm.loginWithEmailAllowed>
    ${msg("username")}
  <#elseif !realm.registrationEmailAsUsername>
    ${msg("usernameOrEmail")}
  <#else>
    ${msg("email")}
  </#if>
</#assign>

<!doctype html>
<html lang="${((locale.currentLanguageTag)!'nl')}" class="mf-auth-html">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <meta name="robots" content="noindex, nofollow" />
    <title>${msg("loginTitle", (realm.displayName!'Metafactory'))}</title>
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;700;800&family=JetBrains+Mono:wght@500;700;800&display=swap" rel="stylesheet" />
    <link rel="stylesheet" href="${url.resourcesPath}/css/metafactory-login.css" />
  </head>

  <body class="mf-auth-body">
    <div class="mf-scanlines" aria-hidden="true"></div>
    <div class="mf-grid" aria-hidden="true"></div>
    <div class="mf-puff mf-puff-a" aria-hidden="true"></div>
    <div class="mf-puff mf-puff-b" aria-hidden="true"></div>
    <div class="mf-puff mf-puff-c" aria-hidden="true"></div>

    <main class="mf-auth-shell">
      <section class="mf-auth-card" aria-labelledby="mf-login-title">
        <div class="mf-frame" aria-hidden="true">
          <span class="mf-corner mf-corner-tl"></span>
          <span class="mf-corner mf-corner-tr"></span>
          <span class="mf-corner mf-corner-bl"></span>
          <span class="mf-corner mf-corner-br"></span>
        </div>

        <aside class="mf-orb-panel" aria-hidden="true">
          <div class="mf-brand-mark">M</div>
          <div class="mf-orb">
            <span class="mf-orb-halo"></span>
            <span class="mf-orb-core"></span>
            <span class="mf-orb-grid"></span>
          </div>
          <p class="mf-orb-label">AUTHENTICATED ACCESS</p>
          <p class="mf-orb-copy">Secure launch sequence for the Metafactory portal.</p>
        </aside>

        <section class="mf-form-panel">
          <div class="mf-brand-row">
            <span class="mf-brand-kicker">MetaFactory Realm</span>
            <span class="mf-system-status">ONLINE</span>
          </div>

          <header class="mf-form-header">
            <p class="mf-eyebrow">Identity verification</p>
            <h1 id="mf-login-title">${msg("doLogIn")}</h1>
            <p>Log in to open your futuristic workspace.</p>
          </header>

          <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
            <div class="mf-alert mf-alert-${message.type}" role="alert">
              <span class="mf-alert-pulse" aria-hidden="true"></span>
              <span>${kcSanitize(message.summary)?no_esc}</span>
            </div>
          </#if>

            <form id="kc-form-login" class="mf-login-form" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
              <#if !usernameHidden??>
                <div class="mf-field">
                  <label for="username">${usernameLabel}</label>
                  <div class="mf-input-shell">
                    <input
                      tabindex="1"
                      id="username"
                      name="username"
                      value="${(login.username!'')}"
                      type="text"
                      autofocus
                      autocomplete="${(enableWebAuthnConditionalUI?has_content)?then('username webauthn', 'username')}"
                      aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                      dir="ltr"
                    />
                    <span class="mf-input-glow" aria-hidden="true"></span>
                  </div>
                </div>
              </#if>

              <div class="mf-field">
                <label for="password">${msg("password")}</label>
                <div class="mf-input-shell mf-password-shell" dir="ltr">
                  <input
                    tabindex="2"
                    id="password"
                    name="password"
                    type="password"
                    autocomplete="current-password"
                    aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                  />
                  <button class="mf-password-toggle" type="button" aria-label="${msg('showPassword')}" aria-controls="password" data-mf-password-toggle>
                    <span data-mf-toggle-label>${msg('showPassword')}</span>
                  </button>
                  <span class="mf-input-glow" aria-hidden="true"></span>
                </div>
              </div>

              <#if messagesPerField.existsError('username','password')>
                <div id="input-error" class="mf-field-error" aria-live="polite">
                  ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                </div>
              </#if>

              <div class="mf-options-row">
                <#if realm.rememberMe && !usernameHidden??>
                  <label class="mf-checkbox" for="rememberMe">
                    <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if> />
                    <span>${msg("rememberMe")}</span>
                  </label>
                <#else>
                  <span></span>
                </#if>

                <#if realm.resetPasswordAllowed>
                  <a tabindex="4" class="mf-link" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
                </#if>
              </div>

              <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if> />

              <button tabindex="5" class="mf-submit" name="login" id="kc-login" type="submit">
                <span>${msg("doLogIn")}</span>
                <span class="mf-submit-arrow" aria-hidden="true">→</span>
              </button>
            </form>

          <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration-container" class="mf-registration">
              <span>${msg("noAccount")}</span>
              <a tabindex="6" class="mf-link" href="${url.registrationUrl}">${msg("doRegister")}</a>
            </div>
          </#if>

          <#if social?? && social.providers?has_content>
            <div id="kc-social-providers" class="mf-social-providers">
              <div class="mf-divider"><span>${msg("identity-provider-login-label")}</span></div>
              <ul>
                <#list social.providers as p>
                  <li>
                    <a id="social-${p.alias}" class="mf-social-link" type="button" href="${p.loginUrl}">
                      <span>${p.displayName!}</span>
                    </a>
                  </li>
                </#list>
              </ul>
            </div>
          </#if>
        </section>
      </section>
    </main>

    <script src="${url.resourcesPath}/js/metafactory-login.js"></script>
  </body>
</html>
