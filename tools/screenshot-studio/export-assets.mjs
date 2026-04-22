import { copyFile, mkdir, rm, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { spawn } from "node:child_process";
import puppeteer from "puppeteer";

const PORT = 4312;
const repoRoot = process.env.CUE_REPO_ROOT;
const studioRoot = process.cwd();

if (!repoRoot) {
  throw new Error("CUE_REPO_ROOT is required");
}

const server = spawn("npm", ["run", "dev", "--", "--hostname", "localhost", "--port", String(PORT)], {
  cwd: studioRoot,
  stdio: "inherit",
  shell: true,
});

async function syncStudioInputAssets() {
  const iconSource = join(repoRoot, "branding", "icons", "exports", "app-store", "cue-icon-1024.png");
  const iosSourceDir = join(repoRoot, "branding", "screenshots", "source", "captures", "ios", "iphone");
  const androidSourceDir = join(repoRoot, "branding", "screenshots", "source", "captures", "android", "phone");

  const iconTarget = join(studioRoot, "public", "app-icon.png");
  const iosTargetDir = join(studioRoot, "public", "screenshots", "apple", "iphone", "en");
  const androidTargetDir = join(studioRoot, "public", "screenshots", "android", "phone", "en");

  await mkdir(iosTargetDir, { recursive: true });
  await mkdir(androidTargetDir, { recursive: true });

  await copyFile(iconSource, iconTarget);

  for (const scene of ["onboarding", "demo-home", "demo-trust"]) {
    await copyFile(join(iosSourceDir, `${scene}.png`), join(iosTargetDir, `${scene}.png`));
    await copyFile(join(androidSourceDir, `${scene}.png`), join(androidTargetDir, `${scene}.png`));
  }
}

async function waitForServer() {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      const response = await fetch(`http://127.0.0.1:${PORT}`);
      if (response.ok) return;
    } catch {
      // wait and retry
    }
    try {
      const response = await fetch(`http://localhost:${PORT}`);
      if (response.ok) return;
    } catch {
      // wait and retry
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }

  throw new Error("Timed out waiting for local screenshot studio");
}

async function writeAssets(records) {
  const appStoreDir = join(repoRoot, "branding", "screenshots", "exports", "app-store");
  const playStoreDir = join(repoRoot, "branding", "screenshots", "exports", "play-store");

  await rm(appStoreDir, { recursive: true, force: true });
  await rm(playStoreDir, { recursive: true, force: true });
  await mkdir(appStoreDir, { recursive: true });
  await mkdir(playStoreDir, { recursive: true });

  for (const record of records) {
    const [group, fileName] = record.name.split("/");
    const targetDir = group === "app-store" ? appStoreDir : playStoreDir;
    const buffer = Buffer.from(record.dataUrl.replace(/^data:image\/png;base64,/, ""), "base64");
    await writeFile(join(targetDir, fileName), buffer);
  }
}

try {
  await syncStudioInputAssets();
  await waitForServer();

  const browser = await puppeteer.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1200, deviceScaleFactor: 1 } });
  await page.goto(`http://localhost:${PORT}`, { waitUntil: "networkidle0" });
  await page.waitForFunction(() => Boolean(window.cueStudio?.exportAllAssets), { timeout: 120000 });
  const records = await page.evaluate(async () => window.cueStudio.exportAllAssets());
  await browser.close();

  await writeAssets(records);
} finally {
  server.kill("SIGTERM");
}
