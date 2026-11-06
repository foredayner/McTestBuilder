// 놋대야: a brass washbasin of water with a towel and a soap dish, on the floor in the middle of its block
def z = -0.5
ai.addCone('#c9a24a', 0, 0, z, 0.2, 0.3, 0.1)                     // the basin, wider at the top
ai.addCylinder('#a8823a', 0, 0.1, z, 0.31, 0.015)                 // its rim
ai.addCylinder('#9ac0d0', 0, 0.08, z, 0.27, 0.005)                // water
ai.addRoundBox('#f4f1ea', 0.18, 0.11, z - 0.1, 0.14, 0.02, 0.24, 0.01)   // towel over the rim
ai.addPart('#f4f1ea', 0.31, 0.0, z - 0.1, 0.02, 0.12, 0.24)       // hanging down outside
ai.addCylinder('#e8e2d2', -0.32, 0, z + 0.25, 0.06, 0.02)         // soap dish
ai.addRoundBox('#e8d8a8', -0.36, 0.02, z + 0.22, 0.08, 0.03, 0.06, 0.012)   // soap
