// espresso machine with a cup; stands on a kitchen counter top (y = 0), back at z = 0
def steel = '#c8cacc'
def black = '#1c1c1c'
ai.addRoundBox(steel, -0.18, 0, -0.42, 0.36, 0.34, 0.3, 0.03)        // body
ai.addRoundBox(black, -0.18, 0.34, -0.42, 0.36, 0.04, 0.3, 0.015)    // top tray for warming cups
ai.addPart(black, -0.16, 0.04, -0.425, 0.32, 0.05, 0.12)             // drip tray
ai.addPart('#8a8c8e', -0.15, 0.09, -0.425, 0.3, 0.005, 0.1)          // its grille
ai.addCylinder('#8a8c8e', -0.04, 0.22, -0.36, 0.045, 0.04)           // group head
ai.addCapsule(black, -0.04, 0.2, -0.36, -0.04, 0.2, -0.5, 0.012)     // portafilter handle
ai.addCylinder('#f4f1ea', -0.04, 0.095, -0.36, 0.035, 0.06)          // the cup under it
ai.addCylinder('#4a2a16', -0.04, 0.15, -0.36, 0.03, 0.004)           // coffee
ai.addCapsule('#8a8c8e', 0.12, 0.26, -0.4, 0.13, 0.12, -0.46, 0.008) // steam wand
ai.addSphere('#3a8a3a', -0.12, 0.3, -0.424, 0.012)                   // power light
ai.addSphere(black, 0.08, 0.28, -0.424, 0.02)                        // knobs
ai.addSphere(black, 0.14, 0.28, -0.424, 0.02)
