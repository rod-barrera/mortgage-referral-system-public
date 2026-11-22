document.querySelectorAll<HTMLButtonElement>("[data-toggle-password]").forEach((button) => {
  const target = button.dataset.togglePassword;
  const field = target ? document.getElementById(target) : null;
  if (!(field instanceof HTMLInputElement)) return;

  button.hidden = false;
  button.addEventListener("click", () => {
    const visible = field.type === "password";
    field.type = visible ? "text" : "password";
    button.textContent = visible ? "Ocultar" : "Mostrar";
    button.setAttribute("aria-pressed", String(visible));
    button.setAttribute("aria-label", visible ? "Ocultar contraseña" : "Mostrar contraseña");
  });
});

document.querySelectorAll<HTMLFormElement>("form[data-confirm]").forEach((form) => {
  form.addEventListener("submit", (event) => {
    const message = form.dataset.confirm;
    if (message && !window.confirm(message)) event.preventDefault();
  });
});
