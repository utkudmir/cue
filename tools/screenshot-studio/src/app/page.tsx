"use client";

import { toPng } from "html-to-image";
import type { CSSProperties, ReactElement, ReactNode } from "react";
import { useEffect, useMemo, useRef, useState } from "react";

const W = 1320;
const H = 2868;
const AW = 1080;
const AH = 1920;
const FGW = 1024;
const FGH = 500;

const LOCALES = ["en"] as const;
type Locale = (typeof LOCALES)[number];
type Device = "iphone" | "android" | "feature-graphic";
type CanvasKind = "iphone" | "android";

const THEMES = {
  "cue-editorial": {
    bg: "#EDF3FF",
    bgAlt: "#0F1B2D",
    panel: "rgba(255,255,255,0.88)",
    fg: "#11243B",
    muted: "#62748B",
    accent: "#4C7DFF",
    accentSoft: "#B8D2FF",
    ink: "#0F1B2D",
  },
} as const;

const IMAGE_PATHS = [
  "/app-icon.png",
  "/screenshots/apple/iphone/en/onboarding.png",
  "/screenshots/apple/iphone/en/demo-home.png",
  "/screenshots/apple/iphone/en/demo-trust.png",
  "/screenshots/android/phone/en/onboarding.png",
  "/screenshots/android/phone/en/demo-home.png",
  "/screenshots/android/phone/en/demo-trust.png",
] as const;

const imageCache: Record<string, string> = {};

function img(path: string): string {
  return imageCache[path] || path;
}

async function preloadAllImages() {
  await Promise.all(
    IMAGE_PATHS.map(async (path) => {
      if (imageCache[path]) return;
      const response = await fetch(path);
      const blob = await response.blob();
      const dataUrl = await new Promise<string>((resolve) => {
        const reader = new FileReader();
        reader.onloadend = () => resolve(reader.result as string);
        reader.readAsDataURL(blob);
      });
      imageCache[path] = dataUrl;
    })
  );
}

type SlideDef = {
  id: string;
  component: (locale: Locale) => ReactElement;
};

type ExportRecord = {
  name: string;
  dataUrl: string;
};

const IPHONE_SLIDES: SlideDef[] = [
  {
    id: "hero",
    component: () => (
      <EditorialSlide
        label="CUE"
        headline={<>Never miss<br />renewal.</>}
        body="A calm, local-first reminder for Real-Debrid renewal timing."
        device={<PhoneScreenshot src={img("/screenshots/apple/iphone/en/onboarding.png")} />}
      />
    ),
  },
  {
    id: "service-fit",
    component: () => (
      <EditorialSlide
        label="REAL-DEBRID"
        headline={<>Built for<br />one service.</>}
        body="Cue focuses on one job for one audience instead of chasing a generic account dashboard story."
        device={<PhoneScreenshot src={img("/screenshots/apple/iphone/en/onboarding.png")} variant="tilted" />}
      />
    ),
  },
  {
    id: "visibility",
    component: () => (
      <EditorialSlide
        label="EXPIRY"
        headline={<>See it<br />before it slips.</>}
        body="Put renewal timing front and center so premium status never fades into the background."
        device={<PhoneScreenshot src={img("/screenshots/apple/iphone/en/demo-home.png")} />}
      />
    ),
  },
  {
    id: "reminders",
    component: () => (
      <DarkContrastSlide
        label="REMINDERS"
        headline={<>Know when<br />to renew.</>}
        body="Pick the days that matter and keep renewal timing visible without handing your data to a backend."
        pills={["3 days", "1 day", "Expiry day"]}
      />
    ),
  },
  {
    id: "trust",
    component: () => (
      <EditorialSlide
        label="TRUST"
        headline={<>Trust what<br />it handles.</>}
        body="Privacy, diagnostics, and non-affiliation stay visible from the in-app Trust Center."
        device={<PhoneScreenshot src={img("/screenshots/apple/iphone/en/demo-trust.png")} />}
      />
    ),
  },
];

const ANDROID_SLIDES: SlideDef[] = [
  {
    id: "hero",
    component: () => (
      <EditorialSlide
        canvas="android"
        label="CUE"
        headline={<>Never miss<br />renewal.</>}
        body="A calm, local-first reminder for Real-Debrid renewal timing."
        device={<AndroidPhoneScreenshot src={img("/screenshots/android/phone/en/demo-home.png")} compact />}
      />
    ),
  },
  {
    id: "service-fit",
    component: () => (
      <EditorialSlide
        canvas="android"
        label="REAL-DEBRID"
        headline={<>Built for<br />one service.</>}
        body="Cue is intentionally narrow: account status, expiry visibility, and renewal reminders."
        device={<AndroidPhoneScreenshot src={img("/screenshots/android/phone/en/onboarding.png")} variant="tilted" compact />}
      />
    ),
  },
  {
    id: "visibility",
    component: () => (
      <EditorialSlide
        canvas="android"
        label="EXPIRY"
        headline={<>See it<br />before it slips.</>}
        body="A curated demo shows premium active and expiring soon so the value lands instantly."
        device={<AndroidPhoneScreenshot src={img("/screenshots/android/phone/en/demo-home.png")} compact />}
      />
    ),
  },
  {
    id: "reminders",
    component: () => (
      <DarkContrastSlide
        canvas="android"
        label="REMINDERS"
        headline={<>Know when<br />to renew.</>}
        body="Choose the reminder cadence that fits your routine and keep it local to the device."
        pills={["3 days", "1 day", "Expiry day"]}
      />
    ),
  },
  {
    id: "trust",
    component: () => (
      <EditorialSlide
        canvas="android"
        label="TRUST"
        headline={<>Trust what<br />it handles.</>}
        body="Privacy, diagnostics, and non-affiliation stay visible from the in-app Trust Center."
        device={<AndroidPhoneScreenshot src={img("/screenshots/android/phone/en/demo-trust.png")} compact />}
      />
    ),
  },
];

function theme() {
  return THEMES["cue-editorial"];
}

function baseCanvasStyle(width: number, height: number): CSSProperties {
  const t = theme();
  return {
    width,
    height,
    position: "relative",
    overflow: "hidden",
    background: `radial-gradient(circle at 20% 20%, ${t.accentSoft} 0%, ${t.bg} 32%, #F8FBFF 100%)`,
    color: t.fg,
  };
}

function canvasSize(canvas: CanvasKind) {
  if (canvas === "android") {
    return { width: AW, height: AH, compact: true };
  }
  return { width: W, height: H, compact: false };
}

function Badge({ children, compact = false }: { children: ReactNode; compact?: boolean }) {
  const t = theme();
  return (
    <span
      style={{
        display: "inline-flex",
        alignItems: "center",
        padding: compact ? "10px 14px" : "12px 18px",
        borderRadius: 999,
        background: "rgba(255,255,255,0.78)",
        border: `1px solid rgba(17,36,59,0.08)`,
        color: t.fg,
        fontSize: compact ? 20 : 28,
        fontWeight: 700,
      }}
    >
      {children}
    </span>
  );
}

function Caption({ label, headline, body, dark = false, compact = false }: { label: string; headline: ReactNode; body: string; dark?: boolean; compact?: boolean }) {
  const t = theme();
  const fg = dark ? "#F8FBFF" : t.fg;
  const muted = dark ? "rgba(248,251,255,0.78)" : t.muted;

  return (
    <div style={{ position: "absolute", left: compact ? 54 : 72, top: compact ? 62 : 84, width: compact ? 500 : 620, zIndex: 5 }}>
      <div style={{ color: dark ? t.accentSoft : t.accent, fontWeight: 800, letterSpacing: compact ? 2.4 : 3, fontSize: compact ? 22 : 28 }}>{label}</div>
      <div style={{ marginTop: compact ? 18 : 24, fontSize: compact ? 88 : 126, lineHeight: 0.92, fontWeight: 900, color: fg }}>{headline}</div>
      <div style={{ marginTop: compact ? 20 : 28, fontSize: compact ? 30 : 42, lineHeight: 1.18, color: muted }}>{body}</div>
    </div>
  );
}

function PhoneFrame({ children, style, compact = false }: { children: ReactNode; style?: CSSProperties; compact?: boolean }) {
  return (
    <div
      style={{
        position: "absolute",
        width: compact ? 520 : 680,
        aspectRatio: "9 / 19.5",
        borderRadius: compact ? "42px" : "56px",
        background: "linear-gradient(165deg, #21252d 0%, #090D15 100%)",
        boxShadow: "0 30px 80px rgba(9,13,21,0.32)",
        overflow: "hidden",
        ...style,
      }}
    >
      <div
        style={{
          position: "absolute",
          left: "3.2%",
          top: "1.8%",
          width: "93.6%",
          height: "96.1%",
          borderRadius: compact ? "30px" : "42px",
          overflow: "hidden",
          background: "#fff",
        }}
      >
        {children}
      </div>
    </div>
  );
}

function PhoneScreenshot({ src, variant, compact = false }: { src: string; variant?: "tilted"; compact?: boolean }) {
  return (
    <PhoneFrame
      compact={compact}
      style={{
        right: variant === "tilted" ? (compact ? -8 : -20) : (compact ? 24 : 40),
        bottom: variant === "tilted" ? (compact ? -24 : -40) : (compact ? -80 : -120),
        transform: variant === "tilted" ? "rotate(-7deg)" : "none",
      }}
    >
      <img src={src} alt="Cue screenshot" style={{ width: "100%", height: "100%", objectFit: "cover", objectPosition: "top" }} draggable={false} />
    </PhoneFrame>
  );
}

function AndroidPhoneScreenshot({ src, variant, compact = false }: { src: string; variant?: "tilted"; compact?: boolean }) {
  return (
    <PhoneFrame
      compact={compact}
      style={{
        right: variant === "tilted" ? (compact ? -8 : -20) : (compact ? 24 : 40),
        bottom: variant === "tilted" ? (compact ? -30 : -50) : (compact ? -90 : -120),
        transform: variant === "tilted" ? "rotate(-7deg)" : "none",
      }}
    >
      <img src={src} alt="Cue screenshot" style={{ width: "100%", height: "100%", objectFit: "cover", objectPosition: "top" }} draggable={false} />
    </PhoneFrame>
  );
}

function EditorialSlide({ label, headline, body, device, canvas = "iphone" }: { label: string; headline: ReactNode; body: string; device: ReactNode; canvas?: CanvasKind }) {
  const size = canvasSize(canvas);
  return (
    <div style={baseCanvasStyle(size.width, size.height)}>
      <Caption label={label} headline={headline} body={body} compact={size.compact} />
      <Glow x={size.compact ? -70 : -90} y={size.compact ? 1320 : 2030} size={size.compact ? 320 : 420} opacity={0.35} />
      {device}
    </div>
  );
}

function DarkContrastSlide({ label, headline, body, pills, canvas = "iphone" }: { label: string; headline: ReactNode; body: string; pills: string[]; canvas?: CanvasKind }) {
  const t = theme();
  const size = canvasSize(canvas);
  return (
    <div
      style={{
        ...baseCanvasStyle(size.width, size.height),
        background: `linear-gradient(145deg, ${t.bgAlt} 0%, #09111D 58%, #152742 100%)`,
      }}
    >
      <Caption label={label} headline={headline} body={body} dark compact={size.compact} />
      <div style={{ position: "absolute", left: size.compact ? 54 : 72, bottom: size.compact ? 92 : 140, display: "flex", gap: size.compact ? 12 : 18, flexWrap: "wrap", width: size.compact ? 560 : 720 }}>
        {pills.map((pill) => (
          <span
            key={pill}
            style={{
              padding: size.compact ? "10px 16px" : "14px 22px",
              borderRadius: 999,
              background: "rgba(255,255,255,0.12)",
              border: "1px solid rgba(255,255,255,0.12)",
              color: "#F8FBFF",
              fontSize: size.compact ? 22 : 30,
              fontWeight: 800,
            }}
          >
            {pill}
          </span>
        ))}
      </div>
      <div style={{ position: "absolute", right: size.compact ? 48 : 72, top: size.compact ? 150 : 200, width: size.compact ? 270 : 360, height: size.compact ? 270 : 360, borderRadius: size.compact ? 56 : 80, background: "rgba(255,255,255,0.06)", display: "grid", placeItems: "center" }}>
        <img src={img("/app-icon.png")} alt="Cue app icon" style={{ width: size.compact ? 160 : 220, height: size.compact ? 160 : 220, borderRadius: size.compact ? 34 : 48 }} draggable={false} />
      </div>
    </div>
  );
}

function IconForwardSlide({ label, headline, body, pills }: { label: string; headline: ReactNode; body: string; pills: string[] }) {
  return (
    <div style={baseCanvasStyle(W, H)}>
      <Caption label={label} headline={headline} body={body} />
      <div style={{ position: "absolute", right: 70, top: 260, display: "grid", gap: 24 }}>
        <img src={img("/app-icon.png")} alt="Cue app icon" style={{ width: 320, height: 320, borderRadius: 72, justifySelf: "end", boxShadow: "0 32px 72px rgba(76,125,255,0.22)" }} draggable={false} />
        <div style={{ display: "grid", gap: 18, justifyItems: "end" }}>
          {pills.map((pill) => (
            <Badge key={pill}>{pill}</Badge>
          ))}
        </div>
      </div>
      <Glow x={780} y={320} size={320} opacity={0.4} />
    </div>
  );
}

function TrustSlide({ label, headline, body }: { label: string; headline: ReactNode; body: string }) {
  const t = theme();
  return (
    <div style={baseCanvasStyle(W, H)}>
      <Caption label={label} headline={headline} body={body} />
      <div style={{ position: "absolute", right: 72, top: 300, width: 420, display: "grid", gap: 18 }}>
        {["No analytics", "No tracking", "No crash SDK", "Manual diagnostics"].map((item) => (
          <div key={item} style={{ padding: "24px 28px", borderRadius: 32, background: theme().panel, border: "1px solid rgba(17,36,59,0.08)", fontSize: 32, fontWeight: 800, color: t.fg }}>
            {item}
          </div>
        ))}
      </div>
      <div style={{ position: "absolute", left: 72, bottom: 120, display: "flex", gap: 20 }}>
        <Badge>One-time purchase</Badge>
        <Badge>Open source</Badge>
      </div>
    </div>
  );
}

function Glow({ x, y, size, opacity }: { x: number; y: number; size: number; opacity: number }) {
  return (
    <div
      style={{
        position: "absolute",
        left: x,
        top: y,
        width: size,
        height: size,
        borderRadius: "999px",
        background: `radial-gradient(circle, rgba(76,125,255,${opacity}) 0%, rgba(76,125,255,0) 72%)`,
        filter: "blur(18px)",
      }}
    />
  );
}

function FeatureGraphic() {
  const t = theme();
  return (
    <div
      style={{
        width: FGW,
        height: FGH,
        position: "relative",
        overflow: "hidden",
        background: `linear-gradient(135deg, ${t.bgAlt} 0%, #17315A 100%)`,
      }}
    >
      <Glow x={700} y={-60} size={300} opacity={0.35} />
      <div style={{ position: "absolute", left: 56, top: 74, display: "flex", alignItems: "center", gap: 24 }}>
        <img src={img("/app-icon.png")} alt="Cue app icon" style={{ width: 132, height: 132, borderRadius: 28 }} draggable={false} />
        <div>
          <div style={{ fontSize: 64, fontWeight: 900, color: "#F8FBFF", lineHeight: 1 }}>Cue</div>
          <div style={{ marginTop: 10, fontSize: 28, color: "rgba(248,251,255,0.82)", lineHeight: 1.1 }}>Renew before it lapses.</div>
        </div>
      </div>
      <div style={{ position: "absolute", right: 56, bottom: 60, display: "grid", gap: 14, justifyItems: "end" }}>
        {["Real-Debrid focused", "Local reminders", "No analytics"].map((item) => (
          <span key={item} style={{ padding: "10px 16px", borderRadius: 999, background: "rgba(255,255,255,0.12)", color: "#F8FBFF", fontSize: 20, fontWeight: 800 }}>
            {item}
          </span>
        ))}
      </div>
    </div>
  );
}

async function captureElement(el: HTMLElement, width: number, height: number) {
  const previousLeft = el.style.left;
  const previousTop = el.style.top;
  el.style.left = "0px";
  el.style.top = "0px";
  el.style.opacity = "1";
  el.style.zIndex = "-1";
  const opts = { width, height, pixelRatio: 1, cacheBust: true };
  await toPng(el, opts);
  const dataUrl = await toPng(el, opts);
  el.style.left = previousLeft;
  el.style.top = previousTop;
  el.style.opacity = "";
  el.style.zIndex = "";
  return dataUrl;
}

function PreviewCard({ width, height, children }: { width: number; height: number; children: ReactNode }) {
  const scale = Math.min(300 / width, 520 / height);
  return (
    <div style={{ width: width * scale, height: height * scale, overflow: "hidden", borderRadius: 20, border: "1px solid rgba(17,36,59,0.1)", background: "white", boxShadow: "0 12px 34px rgba(17,36,59,0.08)" }}>
      <div style={{ width, height, transform: `scale(${scale})`, transformOrigin: "top left" }}>{children}</div>
    </div>
  );
}

export default function Home() {
  const [ready, setReady] = useState(false);
  const [device, setDevice] = useState<Device>("iphone");
  const [locale, setLocale] = useState<Locale>("en");
  const [exporting, setExporting] = useState<string | null>(null);

  const iphoneRefs = useRef<Array<HTMLDivElement | null>>([]);
  const androidRefs = useRef<Array<HTMLDivElement | null>>([]);
  const featureRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    preloadAllImages().then(() => setReady(true));
  }, []);

  const slides = useMemo(() => {
    if (device === "android") return ANDROID_SLIDES;
    if (device === "feature-graphic") return [{ id: "feature-graphic", component: () => <FeatureGraphic /> }];
    return IPHONE_SLIDES;
  }, [device]);

  async function exportCurrentDevice() {
    if (device === "feature-graphic") {
      if (!featureRef.current) return;
      setExporting("feature-graphic");
      const dataUrl = await captureElement(featureRef.current, FGW, FGH);
      download(dataUrl, `01-feature-graphic-${locale}-${FGW}x${FGH}.png`);
      setExporting(null);
      return;
    }

    const currentRefs = device === "iphone" ? iphoneRefs.current : androidRefs.current;
    const width = device === "iphone" ? W : AW;
    const height = device === "iphone" ? H : AH;
    const currentSlides = device === "iphone" ? IPHONE_SLIDES : ANDROID_SLIDES;

    for (let index = 0; index < currentSlides.length; index += 1) {
      const ref = currentRefs[index];
      if (!ref) continue;
      setExporting(`${device}-${index + 1}/${currentSlides.length}`);
      const dataUrl = await captureElement(ref, width, height);
      download(dataUrl, `${String(index + 1).padStart(2, "0")}-${currentSlides[index].id}-${locale}-${width}x${height}.png`);
    }

    setExporting(null);
  }

  async function exportAllAssets(): Promise<ExportRecord[]> {
    const records: ExportRecord[] = [];

    for (const [index, slide] of IPHONE_SLIDES.entries()) {
      const ref = iphoneRefs.current[index];
      if (!ref) continue;
      records.push({
        name: `app-store/${String(index + 1).padStart(2, "0")}-${slide.id}-${W}x${H}.png`,
        dataUrl: await captureElement(ref, W, H),
      });
    }

    for (const [index, slide] of ANDROID_SLIDES.entries()) {
      const ref = androidRefs.current[index];
      if (!ref) continue;
      records.push({
        name: `play-store/${String(index + 1).padStart(2, "0")}-${slide.id}-${AW}x${AH}.png`,
        dataUrl: await captureElement(ref, AW, AH),
      });
    }

    if (featureRef.current) {
      records.push({
        name: `play-store/01-feature-graphic-${FGW}x${FGH}.png`,
        dataUrl: await captureElement(featureRef.current, FGW, FGH),
      });
    }

    return records;
  }

  useEffect(() => {
    if (!ready) return;
    (window as Window & { cueStudio?: { exportAllAssets: () => Promise<ExportRecord[]> } }).cueStudio = {
      exportAllAssets,
    };
  }, [ready]);

  if (!ready) {
    return <div style={{ minHeight: "100vh", display: "grid", placeItems: "center", color: theme().fg }}>Loading images…</div>;
  }

  return (
    <div style={{ minHeight: "100vh", position: "relative", overflowX: "hidden", background: "#EAF1FF" }}>
      <div style={{ position: "sticky", top: 0, zIndex: 30, background: "rgba(255,255,255,0.9)", backdropFilter: "blur(14px)", borderBottom: "1px solid rgba(17,36,59,0.08)", display: "flex", alignItems: "center" }}>
        <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 10, padding: "10px 16px", overflowX: "auto", minWidth: 0 }}>
          <strong style={{ whiteSpace: "nowrap" }}>Cue · Screenshot Studio</strong>
          <select value={locale} onChange={(event) => setLocale(event.target.value as Locale)} style={selectStyle}>
            {LOCALES.map((value) => (
              <option key={value} value={value}>{value.toUpperCase()}</option>
            ))}
          </select>
          {(["iphone", "android", "feature-graphic"] as Device[]).map((value) => (
            <button key={value} onClick={() => setDevice(value)} style={tabStyle(device === value)}>
              {value === "iphone" ? "iPhone" : value === "android" ? "Android" : "Feature Graphic"}
            </button>
          ))}
        </div>
        <div style={{ flexShrink: 0, padding: "10px 16px", borderLeft: "1px solid rgba(17,36,59,0.08)" }}>
          <button onClick={() => void exportCurrentDevice()} style={exportButtonStyle}>
            {exporting ? `Exporting… ${exporting}` : "Export current"}
          </button>
        </div>
      </div>

      <div style={{ padding: 24, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: 20 }}>
        {device === "iphone" && IPHONE_SLIDES.map((slide) => (
          <PreviewCard key={slide.id} width={W} height={H}>{slide.component(locale)}</PreviewCard>
        ))}
        {device === "android" && ANDROID_SLIDES.map((slide) => (
          <PreviewCard key={slide.id} width={AW} height={AH}>{slide.component(locale)}</PreviewCard>
        ))}
        {device === "feature-graphic" && <PreviewCard width={FGW} height={FGH}><FeatureGraphic /></PreviewCard>}
      </div>

      <div style={{ position: "absolute", left: "-20000px", top: 0, width: 40000, height: 40000 }}>
        {IPHONE_SLIDES.map((slide, index) => (
          <div key={slide.id} ref={(node) => { iphoneRefs.current[index] = node; }} style={{ position: "absolute", left: index * (W + 120), top: 0 }}>
            {slide.component(locale)}
          </div>
        ))}
        {ANDROID_SLIDES.map((slide, index) => (
          <div key={slide.id} ref={(node) => { androidRefs.current[index] = node; }} style={{ position: "absolute", left: index * (AW + 120), top: H + 200 }}>
            {slide.component(locale)}
          </div>
        ))}
        <div ref={featureRef} style={{ position: "absolute", left: 0, top: H + AH + 400 }}>
          <FeatureGraphic />
        </div>
      </div>
    </div>
  );
}

function download(dataUrl: string, name: string) {
  const link = document.createElement("a");
  link.href = dataUrl;
  link.download = name;
  link.click();
}

const selectStyle: CSSProperties = {
  fontSize: 12,
  border: "1px solid rgba(17,36,59,0.14)",
  borderRadius: 8,
  padding: "6px 10px",
  background: "white",
};

function tabStyle(active: boolean): CSSProperties {
  return {
    border: "none",
    borderRadius: 8,
    padding: "7px 12px",
    cursor: "pointer",
    background: active ? "#4C7DFF" : "rgba(17,36,59,0.06)",
    color: active ? "white" : "#11243B",
    fontWeight: 700,
    whiteSpace: "nowrap",
  };
}

const exportButtonStyle: CSSProperties = {
  border: "none",
  borderRadius: 10,
  padding: "9px 16px",
  cursor: "pointer",
  background: "#4C7DFF",
  color: "white",
  fontWeight: 800,
};
