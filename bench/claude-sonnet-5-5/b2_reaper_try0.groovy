// Giant reaper battle scythe, about 3.4 tall and 2.2 wide
// Parts: black shaft, bone-grey collar rings, pommel spike, crescent blade (dark spine + silver body + bright edge), top spike
ai.oversize()

// shaft
ai.addCapsule('black_concrete', 0, -0.25, 0, 0, 3.15, 0, 0.055)
ai.addCapsule('gray_concrete', 0, 0.0, 0, 0, 0.55, 0, 0.07)       // grip wrap
ai.addCapsule('gray_concrete', 0, 1.3, 0, 0, 1.4, 0, 0.065)       // second wrapped section
ai.addCapsule('black_concrete', 0, 0.58, 0, 0, 1.28, 0, 0.055)
// pommel spike pointing down
ai.addCapsule('iron_block', 0, -0.2, 0, 0, -0.45, 0, 0.06, 0.012)
ai.addSphere('iron_block', 0, -0.22, 0, 0.075)

// collar at the top
ai.addRoundBox('gray_concrete', -0.1, 2.95, -0.1, 0.2, 0.16, 0.2, 0.05)
ai.addRoundBox('iron_block', -0.12, 3.1, -0.12, 0.24, 0.07, 0.24, 0.03)
ai.addRoundBox('gray_concrete', -0.12, 2.88, -0.12, 0.24, 0.05, 0.24, 0.02)
// small spike on top
ai.addCone('iron_block', 0, 3.15, 0, 0.07, 0.3)

// crescent blade sweeping to +X, curving down to a point
int n = 26
for (int i = 0; i < n; i++) {
    double f = i / (double) n
    double deg = 95 - 125 * f
    double th = Math.toRadians(deg)
    double w = 0.04 + 0.36 * Math.pow(1 - f, 0.85)
    double ox = 0.1 + 1.95 * Math.cos(th)
    double oy = 2.2 + 1.0 * Math.sin(th)
    double cth = Math.cos(th)
    double sth = Math.sin(th)
    // dark back along the outer edge
    ai.addEllipsoid('gray_concrete', ox, oy, 0, 0.09 + w * 0.15, 0.09 + w * 0.15, 0.075)
    // silver body
    double bx = ox - cth * w * 0.5
    double by = oy - sth * w * 0.5
    ai.addEllipsoid('iron_block', bx, by, 0, w * 0.5 + 0.06, w * 0.5 + 0.06, 0.05)
    // bright cutting edge on the inner side
    double ex = ox - cth * w * 0.95
    double ey = oy - sth * w * 0.95
    ai.addEllipsoid('white_concrete', ex, ey, 0, 0.055 + w * 0.08, 0.055 + w * 0.08, 0.035)
}
// blade root linking to collar
ai.addRoundBox('gray_concrete', 0.05, 2.98, -0.06, 0.3, 0.2, 0.12, 0.04)
ai.addCapsule('iron_block', 0.1, 3.05, 0, 0.7, 3.12, 0, 0.07, 0.09)

ai.hitEffect('dust', '#5a0010', 10, 'slash', 3.5)
ai.hitEffect('crit', '#8b0000', 6, 'slash', 3.0)