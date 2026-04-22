async function bootLaunchState() {
  const response = await fetch("data/launch-state.json", { cache: "no-store" })
  const state = await response.json()
  const appStoreLink = document.getElementById("app-store-link")
  const playStoreLink = document.getElementById("play-store-link")
  const launchPill = document.getElementById("launch-pill")

  if (!appStoreLink || !playStoreLink || !launchPill) {
    return
  }

  if (state.status === "live") {
    launchPill.textContent = "Live now"
    if (state.appStoreUrl) {
      appStoreLink.href = state.appStoreUrl
      appStoreLink.classList.remove("is-hidden")
    }
    if (state.playStoreUrl) {
      playStoreLink.href = state.playStoreUrl
      playStoreLink.classList.remove("is-hidden")
    }
  }
}

bootLaunchState().catch(() => {
  const launchPill = document.getElementById("launch-pill")
  if (launchPill) {
    launchPill.textContent = "Coming soon"
  }
})
