// black typewriter with round keys and a sheet of paper; stands on a desk top (y = 0 is the top)
def z = -0.45
def black = '#1c1c1c'
ai.addRoundBox(black, -0.21, 0, z - 0.18, 0.42, 0.08, 0.34, 0.03)          // base
ai.addPart(black, -0.19, 0.06, z - 0.02, 0.38, 0.1, 0.16, 0, -18, 0)      // sloped body
ai.addRoundBox(black, -0.24, 0.14, z + 0.06, 0.48, 0.07, 0.1, 0.03)       // carriage
ai.addCapsule('#2e2e2e', -0.25, 0.2, z + 0.11, 0.25, 0.2, z + 0.11, 0.035) // platen roller
ai.addSphere('#d8d8d8', -0.29, 0.2, z + 0.11, 0.03)                       // roller knobs
ai.addSphere('#d8d8d8', 0.29, 0.2, z + 0.11, 0.03)
ai.addPart('#f6f1e2', -0.13, 0.22, z + 0.12, 0.26, 0.24, 0.006, 0, 8, 0)  // the paper
ai.addPart('#c9a24a', -0.2, 0.065, z - 0.185, 0.4, 0.012, 0.004)          // brass name strip
ai.addCapsule('#c0c0c0', -0.27, 0.2, z + 0.05, -0.33, 0.24, z - 0.02, 0.008)   // return lever
// four rows of round keys, stepping up toward the back
for (int r = 0; r < 4; r++) {
    for (int c = 0; c < 8 + (1 - r % 2); c++) {
        def x = -0.16 + c * 0.04 + (r % 2) * 0.02
        ai.addCylinder('#2e2e2e', x, 0.04 + r * 0.025, z - 0.15 + r * 0.04, 0.004, 0.03)       // key stem
        ai.addCylinder('#f2ead3', x, 0.07 + r * 0.025, z - 0.15 + r * 0.04, 0.014, 0.008)      // key top
    }
}
ai.addRoundBox('#2e2e2e', -0.12, 0.03, z - 0.18, 0.24, 0.015, 0.03, 0.007)   // space bar
