// 석유 곤로: a round kerosene cooking stove with a kettle on it, in the middle of its block; a little light
def z = -0.5
ai.addCylinder('#2a5a8a', 0, 0, z, 0.18, 0.2)                    // blue body
ai.addCylinder('#c8cacc', 0, 0.2, z, 0.19, 0.03)                 // chrome top ring
ai.addCylinder('#3a3a3a', 0, 0.23, z, 0.12, 0.02)                // burner
ai.addCylinder('#ff8a3a', 0, 0.245, z, 0.1, 0.006)               // the blue-orange flame ring
ai.addRoundBox('#c8cacc', -0.05, 0.05, z - 0.19, 0.1, 0.06, 0.02, 0.01)   // window to the wick
ai.addSphere('#2a2a2a', 0.1, 0.12, z - 0.16, 0.022)              // wick knob
ai.addEllipsoid('#c8b070', 0, 0.36, z, 0.13, 0.11, 0.13)         // yellow nickel kettle
ai.addCylinder('#c8b070', 0, 0.26, z, 0.12, 0.04)
ai.addCapsule('#c8b070', 0.1, 0.36, z, 0.22, 0.44, z, 0.018)     // spout
ai.addCapsule('#2a2a2a', -0.08, 0.5, z, 0.08, 0.5, z, 0.012)     // handle
ai.addCapsule('#2a2a2a', -0.08, 0.5, z, -0.1, 0.44, z, 0.012)
ai.addCapsule('#2a2a2a', 0.08, 0.5, z, 0.1, 0.44, z, 0.012)
