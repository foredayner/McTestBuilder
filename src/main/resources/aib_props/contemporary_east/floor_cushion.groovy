// 방석: two square floor cushions stacked, in the middle of its block
def z = -0.5
ai.addRoundBox('#b8302a', -0.3, 0, z - 0.3, 0.6, 0.08, 0.6, 0.04)       // red cushion
ai.addRoundBox('#2a4a8a', -0.28, 0.08, z - 0.28, 0.56, 0.07, 0.56, 0.035) // blue cushion on it
ai.addCylinder('#e8c84a', 0, 0.15, z, 0.06, 0.006)                        // a yellow knot in the middle
ai.addPart('#e8c84a', -0.28, 0.115, z - 0.285, 0.56, 0.008, 0.008)       // piping
ai.addPart('#e8c84a', -0.28, 0.115, z + 0.275, 0.56, 0.008, 0.008)
