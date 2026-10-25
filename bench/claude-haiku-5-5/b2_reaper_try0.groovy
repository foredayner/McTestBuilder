// Giant grim-reaper battle scythe, about 3.2 tall. Requested as giant, so oversize first.
ai.oversize()

// PLAN:
// 1. Two-handed black grip around the hand (y -0.3 to 0.5), thicker than the shaft.
// 2. Long black shaft up to y 2.85.
// 3. Dark silver collar where the blade meets the shaft.
// 4. Crescent blade: a ring of overlapping flat ellipsoids sweeping out to +X,
//    widest near the top, tapering to a point near y 0.7.
// 5. Bright silver cutting edge on the inner (lower) side of the crescent.

// Two-handed grip
ai.addCapsule('black_concrete', 0, -0.3, 0, 0, 0.5, 0, 0.055)

// Long black shaft
ai.addCapsule('black_concrete', 0, 0.45, 0, 0, 2.85, 0, 0.035)

// Collar and dark ornament at the top of the shaft
ai.addEllipsoid('gray_concrete', 0, 2.82, 0, 0.07, 0.1, 0.05)
ai.addEllipsoid('light_gray_concrete', 0, 2.9, 0, 0.045, 0.06, 0.04)

// Crescent blade body: arc centred at (0, 1.5), radius 1.45
for (int i = 0; i <= 26; i++) {
    double t = i / 26.0
    double th = Math.toRadians(92 - t * 127)
    double rr = 1.45 - 0.12
    double x = rr * Math.cos(th)
    double y = 1.5 + rr * Math.sin(th)
    double w = 0.13 * Math.sin(Math.PI * Math.pow(t, 0.6)) + 0.015
    ai.addEllipsoid('iron_block', x, y, 0, w, w, 0.03)
}

// Darker back of the blade (outer curve, slightly behind)
for (int i = 0; i <= 26; i++) {
    double t = i / 26.0
    double th = Math.toRadians(92 - t * 127)
    double rr = 1.45 - 0.09
    double x = rr * Math.cos(th)
    double y = 1.5 + rr * Math.sin(th)
    double w = 0.09 * Math.sin(Math.PI * Math.pow(t, 0.6)) + 0.01
    ai.addEllipsoid('gray_concrete', x, y, -0.02, w * 0.8, w * 0.8, 0.02)
}

// Bright silver cutting edge on the inner (lower) side of the crescent
for (int i = 3; i <= 26; i++) {
    double t = i / 26.0
    double th = Math.toRadians(92 - t * 127)
    double rr = 1.45 - 0.25
    double x = rr * Math.cos(th)
    double y = 1.5 + rr * Math.sin(th)
    ai.addEllipsoid('white_concrete', x, y, 0, 0.02, 0.02, 0.035)
}

// Blood-red slash trail on attack
ai.hitEffect('dust', '#8b0010', 8, 'slash', 2.5)