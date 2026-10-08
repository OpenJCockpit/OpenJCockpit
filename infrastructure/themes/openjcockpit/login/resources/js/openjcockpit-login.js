(function () {
  const password = document.getElementById('password');
  const toggle = document.querySelector('[data-mf-password-toggle]');
  const label = document.querySelector('[data-mf-toggle-label]');

  if (!password || !toggle || !label) {
    return;
  }

  const showText = toggle.getAttribute('aria-label') || 'Show password';
  const hideText = document.documentElement.lang === 'nl' ? 'Verberg wachtwoord' : 'Hide password';

  toggle.addEventListener('click', function () {
    const shouldShow = password.type === 'password';
    password.type = shouldShow ? 'text' : 'password';
    const text = shouldShow ? hideText : showText;
    label.textContent = text;
    toggle.setAttribute('aria-label', text);
    password.focus();
  });
})();
