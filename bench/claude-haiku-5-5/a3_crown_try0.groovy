// Sun crown (태양 왕관): gold band around the head, radiating sun rays, glowing core
ai.groupStart('crown')

// Main gold band wrapping the head (face area y 0.05-0.4 stays clear)
ai.addRoundBox('gold_block', -0.3, 0.40, -0.3, 0.6, 0.09, 0.6, 0.03)
// Darker lower rim under the band
ai.addRoundBox('yellow_terracotta', -0.31, 0.40, -0.31, 0.62, 0.03, 0.62, 0.01)
// Lighter top highlight strip
ai.addRoundBox('yellow_concrete', -0.29, 0.48, -0.29, 0.58, 0.02, 0.58, 0.01)

// Radiating sun rays: alternating long and short spikes around the top
double rayRadius = 0.27;
for (int i = 0; i < 12; i++) {
    double a = i * 2.0 * Math.PI / 12.0;
    double rx = rayRadius * Math.sin(a);
    double rz = rayRadius * Math.cos(a);
    double h = (i % 2 == 0) ? 0.30 : 0.20;
    String rayBlock = (i % 2 == 0) ? 'gold_block' : 'yellow_concrete';
    ai.addCone(rayBlock, rx, 0.46, rz, 0.06, h);
    // small gem tip on each long ray
    if (i % 2 == 0) {
        ai.addEllipsoid('diamond_block', rx, 0.46 + h + 0.01, rz, 0.03, 0.03, 0.03);
    }
}

// Central sun disc on the front of the crown
ai.addEllipsoid('gold_block', 0.0, 0.6, -0.31, 0.1, 0.1, 0.03);
ai.addEllipsoid('glowstone', 0.0, 0.6, -0.335, 0.06, 0.06, 0.02);

// Gem studs around the band
for (int j = 0; j < 8; j++) {
    double b = j * 2.0 * Math.PI / 8.0 + Math.PI / 8.0;
    double sx = 0.31 * Math.sin(b);
    double sz = 0.31 * Math.cos(b);
    ai.addEllipsoid('amethyst_block', sx, 0.445, sz, 0.025, 0.025, 0.025);
}

ai.groupEnd()

// Glowing crown and golden halo
ai.glow('crown')
ai.aura('#ffd700', 0.6)