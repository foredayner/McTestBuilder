// 전기밥솥: a round electric rice cooker with a rice paddle; stands on a kitchen counter top (y = 0)
def z = -0.45
ai.addCylinder('#f2f0ea', 0, 0, z, 0.16, 0.2)                    // body
ai.addEllipsoid('#e2ded4', 0, 0.2, z, 0.16, 0.07, 0.16)          // domed lid
ai.addCylinder('#8a2a2a', 0, 0.02, z, 0.165, 0.02)               // red band at the foot
ai.addRoundBox('#2a2a2a', -0.07, 0.08, z - 0.17, 0.14, 0.08, 0.02, 0.01)   // control panel
ai.addSphere('#3ac06a', -0.03, 0.12, z - 0.172, 0.008)           // lights
ai.addSphere('#e08a2a', 0.0, 0.12, z - 0.172, 0.008)
ai.addCapsule('#c8cacc', -0.1, 0.25, z, 0.1, 0.25, z, 0.015)     // handle
ai.addRoundBox('#f4f1ea', 0.2, 0, z - 0.06, 0.06, 0.012, 0.18, 0.006)     // rice paddle lying by it
