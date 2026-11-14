const screens = ["home-screen", "set-screen", "share-screen", "open-link-screen", "wish-screen"];
const imageInput = document.getElementById("memory-image");
const imagePreview = document.getElementById("image-preview");
let selectedImage = "";
let currentWish = null;
let unlockTimer = null;

function showScreen(screenId) {
  screens.forEach((id) => document.getElementById(id).classList.toggle("hidden", id !== screenId));
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function makeViewUrl(id) {
  const url = new URL(window.location.href);
  url.hash = `wish=${id}`;
  return url.href;
}

function formatDate(value, options = { dateStyle: "long", timeStyle: "short" }) {
  return new Intl.DateTimeFormat(undefined, options).format(new Date(value));
}

function renderWish(wish) {
  currentWish = wish;
  if (unlockTimer !== null) {
    window.clearTimeout(unlockTimer);
    unlockTimer = null;
  }
  const content = document.getElementById("wish-content");
  const openTime = new Date(wish.openDate).getTime();
  if (Number.isFinite(openTime) && Date.now() < openTime) {
    content.innerHTML = `
      <div class="locked-card">
        <div class="locked-icon" aria-hidden="true">♡</div>
        <h2>A little surprise is waiting.</h2>
        <p>This wish was made just for you. Come back when it's time to open it.</p>
        <div class="locked-time">Your wish opens ${formatDate(wish.openDate)}.</div>
      </div>`;
    const delay = Math.min(openTime - Date.now(), 2147483647);
    unlockTimer = window.setTimeout(() => {
      unlockTimer = null;
      if (currentWish === wish && !document.getElementById("wish-screen").classList.contains("hidden")) {
        renderWish(wish);
      }
    }, delay);
  } else {
    const safeName = escapeHtml(wish.nickname || wish.recipient);
    const image = wish.image ? `<img class="wish-image" src="${wish.image}" alt="A memory shared with ${escapeHtml(wish.recipient)}" />` : "";
    const message = wish.thoughts ? `<p>${escapeHtml(wish.thoughts)}</p>` : "";
    content.innerHTML = `
      <article class="wish-card">
        <div class="wish-top">
          <div class="eyebrow"><span></span> A LITTLE MOMENT FOR YOU</div>
          <h2>Happy ${escapeHtml(wish.occasion)}<br /><em>${safeName}!</em></h2>
          <div class="wish-nickname">${formatDate(wish.eventDate, { dateStyle: "long" })}</div>
          ${image}
        </div>
        <div class="wish-message">
          <div class="wish-memory-label">A NOTE, JUST FOR YOU</div>
          ${message}
          <div class="wish-sign">♡</div>
        </div>
      </article>`;
  }
  showScreen("wish-screen");
}

function validWish(wish) {
  return Boolean(
    wish &&
    typeof wish.recipient === "string" &&
    typeof wish.occasion === "string" &&
    typeof wish.openDate === "string" &&
    typeof wish.eventDate === "string" &&
    typeof wish.thoughts === "string" &&
    (wish.image === "" || (typeof wish.image === "string" && wish.image.startsWith("data:image/")))
  );
}

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, (character) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[character]);
}

async function loadWishFromHash() {
  const match = window.location.hash.match(/^#wish=([0-9a-f-]{36})$/i);
  if (!match) return false;
  const content = document.getElementById("wish-content");
  content.innerHTML = '<div class="locked-card"><p>Opening your little surprise…</p></div>';
  showScreen("wish-screen");
  try {
    const response = await fetch(`/api/wishes/${match[1]}`);
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || "This wish link couldn't be opened.");
    const wish = result.wish;
    if (!validWish(wish)) throw new Error("This link does not contain a valid wish.");
    renderWish(wish);
  } catch (error) {
    document.getElementById("incoming-error").textContent = error.message || "This wish link couldn't be opened.";
    showScreen("open-link-screen");
  }
  return true;
}

document.getElementById("open-set").addEventListener("click", () => showScreen("set-screen"));
document.getElementById("open-view").addEventListener("click", () => showScreen("open-link-screen"));
document.querySelectorAll("[data-home]").forEach((button) => button.addEventListener("click", () => {
  if (window.location.hash.startsWith("#wish=")) history.replaceState(null, "", window.location.pathname + window.location.search);
  showScreen("home-screen");
}));

document.querySelectorAll(".occasion-option").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelectorAll(".occasion-option").forEach((option) => option.classList.remove("selected"));
    button.classList.add("selected");
    document.getElementById("occasion").value = button.dataset.occasion;
  });
});

document.getElementById("thoughts").addEventListener("input", (event) => {
  document.getElementById("thought-count").textContent = event.target.value.length;
});

imageInput.addEventListener("change", () => {
  const file = imageInput.files && imageInput.files[0];
  if (!file) return;
  if (!["image/png", "image/jpeg", "image/webp", "image/gif"].includes(file.type)) {
    document.getElementById("image-name").textContent = "Please choose a JPG, PNG, WEBP, or GIF image.";
    imageInput.value = "";
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    document.getElementById("image-name").textContent = "That image is over 5 MB.";
    imageInput.value = "";
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    selectedImage = String(reader.result);
    imagePreview.src = selectedImage;
    document.getElementById("image-name").textContent = file.name;
    document.getElementById("image-preview-wrap").classList.remove("hidden");
  };
  reader.onerror = () => {
    document.getElementById("image-name").textContent = "Couldn't read that image. Please try another.";
  };
  reader.readAsDataURL(file);
});

document.getElementById("remove-image").addEventListener("click", () => {
  selectedImage = "";
  imageInput.value = "";
  document.getElementById("image-name").textContent = "";
  document.getElementById("image-preview-wrap").classList.add("hidden");
});

document.getElementById("wish-form").addEventListener("submit", (event) => {
  event.preventDefault();
  void createWish(event.currentTarget);
});

async function createWish(form) {
  if (!form.reportValidity()) return;
  document.getElementById("form-error").textContent = "";
  const openDateInput = document.getElementById("open-date");
  const openDate = new Date(openDateInput.value);
  if (!Number.isFinite(openDate.getTime())) {
    openDateInput.setCustomValidity("Choose a valid date and time.");
    openDateInput.reportValidity();
    return;
  }
  openDateInput.setCustomValidity("");
  const wish = {
    recipient: document.getElementById("recipient").value.trim(),
    nickname: document.getElementById("nickname").value.trim(),
    occasion: document.getElementById("occasion").value,
    eventDate: document.getElementById("event-date").value,
    openDate: openDate.toISOString(),
    thoughts: document.getElementById("thoughts").value.trim(),
    image: selectedImage
  };
  const submitButton = form.querySelector('button[type="submit"]');
  submitButton.disabled = true;
  document.getElementById("copy-status").textContent = "";
  try {
    const response = await fetch("/api/wishes", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
      body: new URLSearchParams(wish)
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || "The wish couldn't be saved.");
    currentWish = wish;
    document.getElementById("share-name").textContent = wish.nickname || wish.recipient;
    document.getElementById("share-link").value = makeViewUrl(result.id);
    showScreen("share-screen");
  } catch (error) {
    document.getElementById("form-error").textContent = error.message || "The wish couldn't be saved. Please try again.";
  } finally {
    submitButton.disabled = false;
  }
}

document.getElementById("copy-link").addEventListener("click", async () => {
  const input = document.getElementById("share-link");
  try {
    await navigator.clipboard.writeText(input.value);
    document.getElementById("copy-status").textContent = "Link copied — send it to them when you're ready.";
  } catch {
    input.focus();
    input.select();
    const copied = document.execCommand("copy");
    document.getElementById("copy-status").textContent = copied ? "Link copied — send it to them when you're ready." : "Select and copy the link to share it.";
  }
});

document.getElementById("share-native").addEventListener("click", async () => {
  const shareData = { title: "A little wish for you", text: "Someone made a little moment just for you.", url: document.getElementById("share-link").value };
  if (navigator.share) {
    try { await navigator.share(shareData); }
    catch (error) { if (error.name !== "AbortError") document.getElementById("copy-status").textContent = "Sharing didn't work. You can copy the link instead."; }
  } else {
    document.getElementById("copy-link").click();
  }
});

document.getElementById("preview-wish").addEventListener("click", () => {
  if (currentWish) renderWish(currentWish);
});

document.getElementById("open-incoming").addEventListener("click", async () => {
  const field = document.getElementById("incoming-link");
  const error = document.getElementById("incoming-error");
  error.textContent = "";
  try {
    const parsed = new URL(field.value.trim());
    if (parsed.origin !== window.location.origin) throw new Error("Open this link on the same Little Moments site where it was created.");
    const match = parsed.hash.match(/^#wish=([0-9a-f-]{36})$/i);
    if (!match) throw new Error("That link doesn't look like a Little Moments wish link.");
    history.replaceState(null, "", parsed.pathname + parsed.search + parsed.hash);
    await loadWishFromHash();
  } catch (problem) {
    error.textContent = problem.message || "Please paste a valid wish link.";
  }
});

window.addEventListener("hashchange", () => { void loadWishFromHash(); });
void loadWishFromHash().then((loaded) => {
  if (!loaded) showScreen("home-screen");
});
