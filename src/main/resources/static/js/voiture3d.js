// Draws a stylised 3D car in every <canvas data-voiture3d>. The car is built from code, so no model file is needed.
import * as THREE from '../vendor/three/three.module.min.js';

const TEINTES = [0xff5a1f, 0x2f7fd6, 0xf1efe9, 0x20242e, 0xf2b632];

// x runs from the rear (negative) to the front (positive). All lengths are in metres.
const GABARITS = {
    CITADINE: { long: 3.7, large: 1.7, seuil: 0.32, ceinture: 0.92, toit: 1.46, roue: 0.31, essieux: [-1.2, 1.2], vitre: [-1.45, 1.05, -1.1, 0.35] },
    BERLINE: { long: 4.6, large: 1.8, seuil: 0.3, ceinture: 0.88, toit: 1.4, roue: 0.33, essieux: [-1.45, 1.45], vitre: [-1.75, 1.15, -1.1, 0.35] },
    SUV: { long: 4.5, large: 1.88, seuil: 0.44, ceinture: 1.12, toit: 1.72, roue: 0.38, essieux: [-1.4, 1.4], vitre: [-1.95, 1.05, -1.75, 0.45] },
    UTILITAIRE: { long: 4.7, large: 1.9, seuil: 0.36, ceinture: 1.08, toit: 2.05, roue: 0.34, essieux: [-1.45, 1.55], vitre: [-2.25, 1.3, -2.25, 0.8] },
};

function extruder(points, largeur, materiau, biseau = 0.035) {
    const forme = new THREE.Shape();
    points.forEach(([x, y], i) => (i === 0 ? forme.moveTo(x, y) : forme.lineTo(x, y)));
    forme.closePath();
    const geometrie = new THREE.ExtrudeGeometry(forme, {
        depth: largeur - biseau * 2,
        bevelEnabled: true,
        bevelThickness: biseau,
        bevelSize: biseau,
        bevelSegments: 3,
        curveSegments: 8,
    });
    geometrie.translate(0, 0, -(largeur - biseau * 2) / 2);
    const maille = new THREE.Mesh(geometrie, materiau);
    maille.castShadow = true;
    return maille;
}

function construireRoue(rayon, largeur, pneu, jante) {
    const roue = new THREE.Group();
    const bande = new THREE.Mesh(new THREE.CylinderGeometry(rayon, rayon, largeur, 32), pneu);
    bande.rotation.x = Math.PI / 2;
    roue.add(bande);
    const moyeu = new THREE.Mesh(new THREE.CylinderGeometry(rayon * 0.58, rayon * 0.58, largeur + 0.02, 24), jante);
    moyeu.rotation.x = Math.PI / 2;
    roue.add(moyeu);
    for (let i = 0; i < 5; i += 1) {
        const rayonJante = new THREE.Mesh(new THREE.BoxGeometry(rayon * 0.14, rayon * 1.1, largeur + 0.05), pneu);
        rayonJante.rotation.z = (i / 5) * Math.PI;
        roue.add(rayonJante);
    }
    return roue;
}

function construireVoiture(categorie, teinte) {
    const g = GABARITS[categorie] || GABARITS.SUV;
    const voiture = new THREE.Group();
    const carrosserie = new THREE.MeshPhysicalMaterial({ color: TEINTES[teinte % TEINTES.length], metalness: 0.55, roughness: 0.32, clearcoat: 1, clearcoatRoughness: 0.12 });
    const verre = new THREE.MeshPhysicalMaterial({ color: 0x10141c, metalness: 0.9, roughness: 0.12, clearcoat: 1 });
    const noir = new THREE.MeshStandardMaterial({ color: 0x14161b, roughness: 0.85 });
    const chrome = new THREE.MeshStandardMaterial({ color: 0xc9ced6, metalness: 1, roughness: 0.25 });
    const phare = new THREE.MeshStandardMaterial({ color: 0xfff4d6, emissive: 0xffd88a, emissiveIntensity: 2.4 });
    const feu = new THREE.MeshStandardMaterial({ color: 0x5c0b0b, emissive: 0xff2a1a, emissiveIntensity: 2 });

    const av = g.long / 2;
    const ar = -g.long / 2;
    // Lower body, with a sloping bonnet.
    voiture.add(extruder([
        [ar + 0.05, g.seuil], [ar, g.seuil + 0.22], [ar + 0.04, g.ceinture - 0.06], [ar + 0.2, g.ceinture],
        [g.vitre[1], g.ceinture + 0.02], [av - 0.42, g.ceinture - 0.1], [av - 0.05, g.ceinture - 0.24],
        [av, g.seuil + 0.2], [av - 0.05, g.seuil],
    ], g.large, carrosserie));

    const [basArriere, basAvant, hautArriere, hautAvant] = g.vitre;
    // Glass cabin, narrower than the body, capped by a painted roof.
    voiture.add(extruder([
        [basArriere, g.ceinture], [hautArriere, g.toit - 0.05], [hautAvant, g.toit - 0.05], [basAvant, g.ceinture],
    ], g.large * 0.84, verre, 0.02));
    voiture.add(extruder([
        [hautArriere - 0.06, g.toit - 0.07], [hautArriere - 0.03, g.toit], [hautAvant + 0.03, g.toit], [hautAvant + 0.06, g.toit - 0.07],
    ], g.large * 0.86, carrosserie, 0.02));

    // Wheels sit in dark arches so that they read from any angle.
    const pneu = new THREE.MeshStandardMaterial({ color: 0x0b0c0f, roughness: 0.9 });
    const roues = [];
    g.essieux.forEach((x) => {
        [-1, 1].forEach((cote) => {
            const passage = new THREE.Mesh(new THREE.CylinderGeometry(g.roue * 1.18, g.roue * 1.18, 0.05, 32), noir);
            passage.rotation.x = Math.PI / 2;
            passage.position.set(x, g.roue + 0.02, cote * (g.large / 2 + 0.002));
            voiture.add(passage);
            const roue = construireRoue(g.roue, 0.26, pneu, chrome);
            roue.position.set(x, g.roue, cote * (g.large / 2 - 0.09));
            voiture.add(roue);
            roues.push(roue);
        });
    });

    // Lights, and a dark sill that grounds the body.
    [-1, 1].forEach((cote) => {
        const avant = new THREE.Mesh(new THREE.BoxGeometry(0.06, 0.09, 0.42), phare);
        avant.position.set(av - 0.04, g.ceinture - 0.32, cote * (g.large / 2 - 0.3));
        voiture.add(avant);
        const arriere = new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.11, 0.46), feu);
        arriere.position.set(ar + 0.01, g.ceinture - 0.2, cote * (g.large / 2 - 0.3));
        voiture.add(arriere);
    });
    const bas = new THREE.Mesh(new THREE.BoxGeometry(g.long - 0.3, 0.1, g.large - 0.02), noir);
    bas.position.y = g.seuil + 0.02;
    voiture.add(bas);

    return { voiture, roues, gabarit: g };
}

// Studio reflections, generated here so that no image has to be downloaded.
function creerReflets(renderer) {
    const studio = new THREE.Scene();
    studio.background = new THREE.Color(0x0b0e16);
    [[0, 6, 0, 12, 0.2, 12, 8], [8, 2, 0, 0.2, 5, 8, 3.5], [-8, 3, 2, 0.2, 4, 6, 2.2], [0, 2, 9, 7, 3, 0.2, 2]].forEach(([x, y, z, l, h, p, intensite]) => {
        const panneau = new THREE.Mesh(new THREE.BoxGeometry(l, h, p), new THREE.MeshBasicMaterial({ color: new THREE.Color(intensite, intensite, intensite) }));
        panneau.position.set(x, y, z);
        studio.add(panneau);
    });
    const generateur = new THREE.PMREMGenerator(renderer);
    const texture = generateur.fromScene(studio, 0.04).texture;
    generateur.dispose();
    return texture;
}

function creerSol(fondClair) {
    const toile = document.createElement('canvas');
    toile.width = 512;
    toile.height = 512;
    const contexte = toile.getContext('2d');
    const degrade = contexte.createRadialGradient(256, 256, 40, 256, 256, 256);
    degrade.addColorStop(0, fondClair ? 'rgba(20,24,34,.34)' : 'rgba(0,0,0,.72)');
    degrade.addColorStop(0.55, fondClair ? 'rgba(20,24,34,.08)' : 'rgba(0,0,0,.3)');
    degrade.addColorStop(1, 'rgba(0,0,0,0)');
    contexte.fillStyle = degrade;
    contexte.fillRect(0, 0, 512, 512);
    const ombre = new THREE.Mesh(new THREE.PlaneGeometry(7.4, 7.4), new THREE.MeshBasicMaterial({ map: new THREE.CanvasTexture(toile), transparent: true, depthWrite: false }));
    ombre.rotation.x = -Math.PI / 2;
    ombre.position.y = 0.002;
    return ombre;
}

function creerAnneaux() {
    const anneaux = new THREE.Group();
    const materiau = new THREE.MeshBasicMaterial({ color: 0xff5a1f, transparent: true, opacity: 0.85 });
    const principal = new THREE.Mesh(new THREE.TorusGeometry(3.45, 0.012, 8, 160), materiau);
    principal.rotation.x = Math.PI / 2;
    anneaux.add(principal);
    const tirets = new THREE.Group();
    for (let i = 0; i < 48; i += 1) {
        const tiret = new THREE.Mesh(new THREE.BoxGeometry(0.2, 0.004, 0.018), new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: i % 4 === 0 ? 0.6 : 0.16 }));
        const angle = (i / 48) * Math.PI * 2;
        tiret.position.set(Math.cos(angle) * 3.85, 0.004, Math.sin(angle) * 3.85);
        tiret.rotation.y = -angle + Math.PI / 2;
        tirets.add(tiret);
    }
    anneaux.add(tirets);
    return { anneaux, tirets };
}

function lancer(toile) {
    let renderer;
    try {
        renderer = new THREE.WebGLRenderer({ canvas: toile, antialias: true, alpha: true });
    } catch {
        return; // No WebGL: the drawing placed under the canvas stays visible.
    }
    toile.closest('[data-scene3d]')?.classList.add('scene3d-prete');
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.05;

    const fondClair = toile.dataset.fond === 'clair';
    const scene = new THREE.Scene();
    scene.environment = creerReflets(renderer);
    const camera = new THREE.PerspectiveCamera(30, 1, 0.1, 80);

    const { voiture, gabarit } = construireVoiture(toile.dataset.categorie, Number(toile.dataset.teinte || 0));
    const pivot = new THREE.Group();
    pivot.add(voiture);
    scene.add(pivot);
    scene.add(creerSol(fondClair));
    const { anneaux, tirets } = creerAnneaux();
    if (!fondClair) {
        scene.add(anneaux);
    }

    scene.add(new THREE.HemisphereLight(0xdfe7ff, 0x1a1208, 0.5));
    const cle = new THREE.DirectionalLight(0xfff1dc, 2.4);
    cle.position.set(6, 9, 5);
    scene.add(cle);
    const contre = new THREE.DirectionalLight(0xff7a3d, fondClair ? 0.6 : 2);
    contre.position.set(-7, 3, -6);
    scene.add(contre);

    const distance = fondClair ? 9.2 : 8.6;
    const cible = new THREE.Vector3(0, gabarit.toit * 0.42, 0);

    function redimensionner() {
        const { clientWidth: largeur, clientHeight: hauteur } = toile;
        if (!largeur || !hauteur) {
            return;
        }
        renderer.setSize(largeur, hauteur, false);
        camera.aspect = largeur / hauteur;
        // A narrow canvas moves the camera back so that the whole car stays in frame.
        const recul = Math.max(1, 1.5 / camera.aspect);
        camera.position.set(0, 2.5 * recul, distance * recul);
        camera.lookAt(cible);
        camera.updateProjectionMatrix();
    }
    new ResizeObserver(redimensionner).observe(toile);
    redimensionner();

    const mouvementReduit = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const orientationDepart = -0.62;
    let orientation = orientationDepart;
    let vitesseInertie = 0;
    let glisse = false;
    let dernierX = 0;
    let parallaxe = 0;

    toile.addEventListener('pointerdown', (evenement) => {
        glisse = true;
        dernierX = evenement.clientX;
        toile.setPointerCapture(evenement.pointerId);
    });
    toile.addEventListener('pointermove', (evenement) => {
        if (glisse) {
            vitesseInertie = (evenement.clientX - dernierX) * 0.008;
            orientation += vitesseInertie;
            dernierX = evenement.clientX;
        }
    });
    ['pointerup', 'pointercancel'].forEach((nom) => toile.addEventListener(nom, () => { glisse = false; }));
    window.addEventListener('pointermove', (evenement) => {
        parallaxe = (evenement.clientX / window.innerWidth - 0.5) * 0.5;
    }, { passive: true });

    function dessiner() {
        renderer.render(scene, camera);
    }

    if (mouvementReduit) {
        pivot.rotation.y = orientationDepart;
        dessiner();
        toile.addEventListener('pointermove', () => {
            pivot.rotation.y = orientation;
            dessiner();
        });
        return;
    }

    let visible = true;
    new IntersectionObserver(([entree]) => { visible = entree.isIntersecting; }).observe(toile);

    const debut = performance.now();
    renderer.setAnimationLoop((maintenant) => {
        if (!visible) {
            return;
        }
        const t = (maintenant - debut) / 1000;
        // Entrance: the car rises and settles during the first second and a half.
        const entree = Math.min(t / 1.5, 1);
        const adoucie = 1 - (1 - entree) ** 3;
        pivot.position.y = (1 - adoucie) * -0.5;
        pivot.scale.setScalar(0.86 + 0.14 * adoucie);
        if (!glisse) {
            vitesseInertie *= 0.94;
            orientation += vitesseInertie + 0.0028;
        }
        pivot.rotation.y = orientation + parallaxe;
        voiture.position.y = Math.sin(t * 1.4) * 0.012;
        tirets.rotation.y = -t * 0.12;
        camera.position.x += (parallaxe * 2 - camera.position.x) * 0.05;
        camera.lookAt(cible);
        dessiner();
    });
}

document.querySelectorAll('canvas[data-voiture3d]').forEach(lancer);
