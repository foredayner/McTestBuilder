// 보온병과 쟁반: a flowered thermos and glasses on a round tray; on a table or low-table top (y = 0)
def z = -0.5
ai.addCylinder('#c8cacc', 0, 0, z, 0.2, 0.012)                    // round tray
ai.addCylinder('#d8dadc', 0, 0.012, z, 0.19, 0.004)
ai.addCylinder('#e8e2d2', -0.05, 0.012, z, 0.06, 0.26)            // thermos body
ai.addPart('#c0392b', -0.11, 0.1, z - 0.061, 0.12, 0.06, 0.004)   // a printed flower band
ai.addSphere('#e8c84a', -0.05, 0.13, z - 0.064, 0.012)
ai.addCylinder('#c0392b', -0.05, 0.272, z, 0.04, 0.05)            // red cup-lid
ai.addCapsule('#c8cacc', -0.11, 0.06, z, -0.11, 0.22, z, 0.008)   // handle
ai.addCylinder('#c8e0e8/50', 0.1, 0.012, z - 0.06, 0.03, 0.08)    // glasses
ai.addCylinder('#c8e0e8/50', 0.11, 0.012, z + 0.06, 0.03, 0.08)
