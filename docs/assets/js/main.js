/* ============================================================
   PayLens U — docs interactions
   Nav móvil, scroll progress, reveal on scroll,
   demo interactiva de la matrix de pago mixto (MixedPayment)
   y contador de escaneos del hero.
   ============================================================ */
(function () {
  "use strict";

  /* ---------- Nav móvil ---------- */
  var toggle = document.getElementById("navToggle");
  if (toggle) {
    toggle.addEventListener("click", function () {
      var open = document.body.classList.toggle("nav-open");
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
    });
    document.querySelectorAll(".nav-links a").forEach(function (link) {
      link.addEventListener("click", function () {
        document.body.classList.remove("nav-open");
        toggle.setAttribute("aria-expanded", "false");
      });
    });
  }

  /* ---------- Scroll progress + nav activa ---------- */
  var progress = document.querySelector(".scroll-progress");
  var sections = Array.prototype.slice.call(document.querySelectorAll("section[id]"));
  var navLinks = Array.prototype.slice.call(document.querySelectorAll(".nav-links a"));

  function onScroll() {
    var doc = document.documentElement;
    var max = doc.scrollHeight - window.innerHeight;
    var pct = max > 0 ? (window.scrollY / max) * 100 : 0;
    if (progress) progress.style.width = pct.toFixed(2) + "%";

    var current = "";
    sections.forEach(function (sec) {
      if (window.scrollY >= sec.offsetTop - 120) current = sec.id;
    });
    navLinks.forEach(function (link) {
      var target = (link.getAttribute("href") || "").replace("#", "");
      link.classList.toggle("active", target === current);
    });
  }
  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  /* ---------- Reveal on scroll ---------- */
  var revealEls = Array.prototype.slice.call(document.querySelectorAll(".reveal"));
  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("visible");
            io.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12 }
    );
    revealEls.forEach(function (el) { io.observe(el); });
  } else {
    revealEls.forEach(function (el) { el.classList.add("visible"); });
  }

  /* ============================================================
     Demo: matrix de pago mixto (misma regla que MixedPayment)
     Fila óptima = mayor billete que cubre SIN exceder el total.
     Los sobrepagos nunca son óptimos (se muestran con su vuelto).
     ============================================================ */
  var BILLETES = [1, 5, 10, 20, 50, 100];
  var totalRange = document.getElementById("totalRange");
  var rateRange = document.getElementById("rateRange");
  var totalOut = document.getElementById("totalOut");
  var rateOut = document.getElementById("rateOut");
  var tbody = document.getElementById("payTableBody");
  var resetBtn = document.getElementById("resetDemo");

  function fmt(n) {
    // Formato venezolano: coma decimal, punto de miles.
    return n.toLocaleString("es-VE", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  function usd(n) { return "$" + fmt(n); }
  function bs(n) { return fmt(n) + " Bs"; }

  function render() {
    if (!tbody) return;
    var total = totalRange ? parseFloat(totalRange.value) : 27;
    var rate = rateRange ? parseFloat(rateRange.value) : 40;

    if (totalOut) totalOut.textContent = usd(total);
    if (rateOut) rateOut.textContent = fmt(rate);

    var html = "";
    var optimalIdx = -1;
    for (var i = 0; i < BILLETES.length; i++) {
      if (BILLETES[i] <= total) optimalIdx = i; // el mayor que cubre sin exceder
    }

    for (var j = 0; j < BILLETES.length; j++) {
      var bill = BILLETES[j];
      var over = bill > total;
      var cash = over ? total : bill;              // efectivo aplicado
      var remainder = Math.max(0, total - cash);   // resto por Pago Móvil
      var remainderBs = remainder * rate;
      var change = Math.max(0, bill - total);      // vuelto en $
      var changeBs = change * rate;

      html +=
        '<tr class="' + (j === optimalIdx ? "opt-row" : "") + '">' +
        '<td class="num">' + usd(bill) + (j === optimalIdx ? ' <span class="opt-badge">ÓPTIMO</span>' : "") + "</td>" +
        '<td class="num">' + usd(cash) + "</td>" +
        '<td class="num">' + bs(remainderBs) + "</td>" +
        '<td class="num">' + usd(change) + "</td>" +
        '<td class="num">' + bs(changeBs) + "</td>" +
        "<td></td>" +
        "</tr>";
    }
    tbody.innerHTML = html;
  }

  if (totalRange && rateRange) {
    totalRange.addEventListener("input", render);
    rateRange.addEventListener("input", render);
    if (resetBtn) {
      resetBtn.addEventListener("click", function () {
        totalRange.value = 27;
        rateRange.value = 40;
        render();
      });
    }
    render();
  }
})();
