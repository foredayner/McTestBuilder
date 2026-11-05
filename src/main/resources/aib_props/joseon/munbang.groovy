// 문방사우: inkstone, inkstick, brushes in a holder, a water dropper and paper; stands on a low desk top (y = 0)
def z = -0.5
ai.addRoundBox('#2a2a2e', -0.2, 0, z - 0.1, 0.16, 0.03, 0.22, 0.015)    // the inkstone
ai.addPart('#121214', -0.18, 0.028, z - 0.08, 0.12, 0.004, 0.08)         // its ink well
ai.addPart('#1a1a1a', -0.17, 0.03, z + 0.02, 0.025, 0.025, 0.08, 15, 0, 0)  // inkstick
ai.addCylinder('#6b4a2a', 0.12, 0, z + 0.06, 0.05, 0.14)                 // brush holder
for (int i = 0; i < 3; i++) {
    def a = Math.toRadians(i * 120)
    ai.addCapsule('#c9b07a', 0.12 + 0.02 * Math.cos(a), 0.12, z + 0.06 + 0.02 * Math.sin(a),
        0.12 + 0.035 * Math.cos(a), 0.3, z + 0.06 + 0.035 * Math.sin(a), 0.006)   // brush handles
}
ai.addCapsule('#3a3a3a', 0.02, 0.008, z - 0.12, 0.2, 0.008, z - 0.12, 0.006)   // a brush laid down
ai.addCone('#1a1a1a', 0.2, 0.002, z - 0.12, 0.008, 0.0, 0.03)
ai.addEllipsoid('#7aa0b8', 0.12, 0.035, z - 0.04, 0.04, 0.035, 0.04)    // blue-white water dropper
ai.addCylinder('#7aa0b8', 0.12, 0.065, z - 0.04, 0.008, 0.02)
ai.addPart('#f2ecd8', -0.04, 0, z - 0.24, 0.24, 0.004, 0.16)            // a sheet of hanji
ai.addPart('#2a2a2a', 0.0, 0.004, z - 0.2, 0.012, 0.002, 0.08)          // a few written strokes
ai.addPart('#2a2a2a', 0.04, 0.004, z - 0.21, 0.012, 0.002, 0.1)
