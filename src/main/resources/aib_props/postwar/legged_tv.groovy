// 다리 달린 흑백 TV: a wooden cabinet TV on four legs with sliding doors open, back at z = 0
def wood = '#6b3a1c'
ai.mirrorOn()
ai.addCapsule(wood, 0.3, 0.32, -0.62, 0.34, 0.0, -0.68, 0.018)     // splayed legs
ai.addCapsule(wood, 0.3, 0.32, -0.22, 0.34, 0.0, -0.16, 0.018)
ai.addPart(wood, 0.38, 0.4, -0.71, 0.06, 0.55, 0.02)              // the sliding doors pushed aside
ai.mirrorOff()
ai.addRoundBox(wood, -0.38, 0.32, -0.7, 0.76, 0.66, 0.56, 0.03)   // cabinet
ai.addRoundBox('#3a3a38', -0.3, 0.46, -0.705, 0.42, 0.38, 0.01, 0.04)   // screen, grey glass
ai.addPart('#5a5a56', -0.26, 0.5, -0.708, 0.34, 0.3, 0.004)       // its glow
ai.addPart('#c8c4b8', 0.16, 0.48, -0.706, 0.16, 0.36, 0.006)      // control panel
ai.addSphere('#2a2a2a', 0.24, 0.75, -0.712, 0.03)                 // channel dial
ai.addSphere('#2a2a2a', 0.24, 0.6, -0.712, 0.022)                 // volume knob
ai.addCapsule('#c8cacc', -0.05, 0.98, -0.42, -0.2, 1.3, -0.4, 0.006)   // rabbit-ear antenna
ai.addCapsule('#c8cacc', 0.05, 0.98, -0.42, 0.2, 1.3, -0.44, 0.006)
ai.addEllipsoid('#2a2a2a', 0, 0.99, -0.42, 0.05, 0.02, 0.04)
ai.addRoundBox('#efe6d0', -0.25, 0.98, -0.6, 0.3, 0.005, 0.2, 0.002)    // a lace doily on top
