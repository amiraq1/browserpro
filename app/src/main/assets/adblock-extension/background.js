const BLOCKED_HOSTS = [
  "aaxads.com",
  "adform.net",
  "adnxs.com",
  "adroll.com",
  "adsafeprotected.com",
  "adsrvr.org",
  "advertising.com",
  "amazon-adsystem.com",
  "bidswitch.net",
  "casalemedia.com",
  "criteo.com",
  "criteo.net",
  "doubleclick.net",
  "googleadservices.com",
  "googlesyndication.com",
  "lijit.com",
  "media.net",
  "openx.net",
  "outbrain.com",
  "pubmatic.com",
  "rubiconproject.com",
  "sharethrough.com",
  "smartadserver.com",
  "taboola.com",
  "yieldmo.com"
];

const BLOCKED_HOST_PREFIXES = [
  "ad.",
  "ads.",
  "adserver.",
  "adservice.",
  "advertising."
];

function normalizeHost(hostname) {
  return (hostname || "").toLowerCase().replace(/\.$/, "").replace(/^www\./, "");
}

function hostMatchesBlockedSuffix(host) {
  return BLOCKED_HOSTS.some((blocked) => host === blocked || host.endsWith("." + blocked));
}

function hostMatchesBlockedPrefix(host) {
  return BLOCKED_HOST_PREFIXES.some((prefix) => host.startsWith(prefix));
}

function shouldBlock(details) {
  if (!details || details.type === "main_frame") return false;

  let host = "";
  try {
    host = normalizeHost(new URL(details.url).hostname);
  } catch (e) {
    return false;
  }

  return hostMatchesBlockedSuffix(host) || hostMatchesBlockedPrefix(host);
}

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    return shouldBlock(details) ? { cancel: true } : {};
  },
  { urls: ["<all_urls>"] },
  ["blocking"]
);
