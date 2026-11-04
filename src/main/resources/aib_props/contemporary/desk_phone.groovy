// desk telephone, about 0.24 wide; stands on a table top (y = 0 is the table's top)
def z = -0.3
ai.addRoundBox('#2a2a2a', -0.12, 0, z - 0.1, 0.24, 0.05, 0.2, 0.015)        // base
ai.addPart('#2a2a2a', -0.12, 0.03, z - 0.05, 0.24, 0.05, 0.15, 0, -14, 0)   // sloped top
ai.addPart('#3a3a3a', -0.11, 0.08, z + 0.0, 0.22, 0.03, 0.08)               // handset cradle
// handset: earpiece and mouthpiece joined by a handle
ai.addCapsule('#202020', -0.09, 0.12, z + 0.04, 0.09, 0.12, z + 0.04, 0.022)
ai.addRoundBox('#202020', -0.12, 0.095, z + 0.0, 0.06, 0.04, 0.08, 0.015)
ai.addRoundBox('#202020', 0.06, 0.095, z + 0.0, 0.06, 0.04, 0.08, 0.015)
// keypad: 3 x 4 buttons
for (int r = 0; r < 4; r++) {
    for (int c = 0; c < 3; c++) {
        ai.addPart('#d0d0d0', -0.05 + c * 0.035, 0.065 - r * 0.009, z - 0.085 + r * 0.025, 0.022, 0.012, 0.018, 0, -14, 0)
    }
}
ai.addTube('#202020', [[-0.12, 0.03, z - 0.02], [-0.16, 0.02, z - 0.04], [-0.17, 0.08, z + 0.02], [-0.12, 0.12, z + 0.04]], 0.006)  // cord
