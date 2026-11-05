// Motion and small conveniences of the public site. Every page stays usable if this file does not load.
const mouvementReduit = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

// The 3D code is heavy: it is only fetched on the pages that show a car.
if (document.querySelector('canvas[data-voiture3d]')) {
    import('./voiture3d.js');
}

const entete = document.querySelector('.site-entete');
if (entete) {
    const suivreDefilement = () => entete.classList.toggle('site-entete-defile', window.scrollY > 24);
    suivreDefilement();
    window.addEventListener('scroll', suivreDefilement, { passive: true });
}

const boutonMenu = document.querySelector('[data-menu-bouton]');
if (boutonMenu) {
    boutonMenu.addEventListener('click', () => {
        const ouvert = document.body.classList.toggle('menu-ouvert');
        boutonMenu.setAttribute('aria-expanded', String(ouvert));
    });
}

// Elements only start hidden once this script runs, so nothing stays invisible without it.
if (!mouvementReduit) {
    const aReveler = document.querySelectorAll('[data-reveler]');
    const observateur = new IntersectionObserver((entrees) => {
        entrees.forEach((entree) => {
            if (entree.isIntersecting) {
                entree.target.classList.add('revele');
                observateur.unobserve(entree.target);
            }
        });
    }, { threshold: 0.12, rootMargin: '0px 0px -6% 0px' });
    aReveler.forEach((element, rang) => {
        element.classList.add('a-reveler');
        element.style.setProperty('--retard', `${(rang % 6) * 60}ms`);
        observateur.observe(element);
    });

    // Cards lean towards the pointer.
    document.querySelectorAll('[data-inclinable]').forEach((carte) => {
        carte.addEventListener('pointermove', (evenement) => {
            const zone = carte.getBoundingClientRect();
            const x = (evenement.clientX - zone.left) / zone.width - 0.5;
            const y = (evenement.clientY - zone.top) / zone.height - 0.5;
            carte.style.setProperty('--incline-x', `${(-y * 6).toFixed(2)}deg`);
            carte.style.setProperty('--incline-y', `${(x * 6).toFixed(2)}deg`);
        });
        carte.addEventListener('pointerleave', () => {
            carte.style.removeProperty('--incline-x');
            carte.style.removeProperty('--incline-y');
        });
    });
}

// A start date pulls the end date along, so that the period can never be entered backwards.
document.querySelectorAll('[data-periode]').forEach((formulaire) => {
    const debut = formulaire.querySelector('[name="debut"]');
    const fin = formulaire.querySelector('[name="fin"]');
    if (debut && fin) {
        debut.addEventListener('change', () => {
            fin.min = debut.value;
            if (fin.value && fin.value < debut.value) {
                fin.value = debut.value;
            }
        });
    }
});

// Card form: spaces every 4 digits, a slash in the expiry, and a live preview.
const numero = document.querySelector('[data-carte-numero]');
if (numero) {
    numero.addEventListener('input', () => {
        numero.value = numero.value.replace(/\D/g, '').slice(0, 19).replace(/(\d{4})(?=\d)/g, '$1 ');
        document.querySelector('[data-apercu-numero]').textContent = numero.value || '•••• •••• •••• ••••';
    });
    const expiration = document.querySelector('[data-carte-expiration]');
    expiration.addEventListener('input', () => {
        const chiffres = expiration.value.replace(/\D/g, '').slice(0, 4);
        expiration.value = chiffres.length > 2 ? `${chiffres.slice(0, 2)}/${chiffres.slice(2)}` : chiffres;
        document.querySelector('[data-apercu-expiration]').textContent = expiration.value || 'MM/AA';
    });
    const titulaire = document.querySelector('[data-carte-titulaire]');
    titulaire.addEventListener('input', () => {
        document.querySelector('[data-apercu-titulaire]').textContent = titulaire.value || 'Titulaire';
    });
    document.querySelector('[data-carte-test]')?.addEventListener('click', () => {
        numero.value = '4242 4242 4242 4242';
        expiration.value = '12/30';
        titulaire.value = titulaire.value || 'Client Démo';
        document.querySelector('[data-carte-cryptogramme]').value = '123';
        [numero, expiration, titulaire].forEach((champ) => champ.dispatchEvent(new Event('input')));
    });
}
