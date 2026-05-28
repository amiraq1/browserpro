(function() {
    const styleId = 'nabd-dark-mode-style';
    if (document.getElementById(styleId)) return;

    const style = document.createElement('style');
    style.id = styleId;
    style.textContent = `
        html {
            filter: invert(1) hue-rotate(180deg) !important;
        }
        img, video, iframe, canvas, [style*="background-image"] {
            filter: invert(1) hue-rotate(180deg) !important;
        }
    `;
    document.documentElement.appendChild(style);
    console.log("Dark Mode Helper injected");
})();
