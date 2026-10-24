// void scythe staff, about 1.8 long; hand at (0, 0, 0), shaft up (+Y), crescent blade faces -Z

// shaft and pommel
ai.addCapsule('blackstone', 0, -0.2, 0, 0, 1.4, 0, 0.035)                   // shaft
ai.addSphere('obsidian', 0, -0.22, 0, 0.05)                                 // pommel
ai.addCylinder('gold_block', 0, 0.46, 0, 0.05, 0.03)                        // lower ring
ai.addCylinder('gold_block', 0, 1.0, 0, 0.045, 0.03)                        // upper ring
ai.addRoundBox('dark_oak_planks', -0.04, -0.02, -0.04, 0.08, 0.2, 0.08, 0.02) // grip wrap (dark leather look)

// socket where the blade joins the shaft
ai.addCone('blackstone', 0, 1.34, 0, 0.055, 0.045, 0.1)                     // collar
ai.addRoundBox('blackstone', -0.05, 1.36, -0.05, 0.1, 0.2, 0.1, 0.02)       // head socket

// crescent blade (animated, rippling like a dark mist)
ai.groupStart('blade')
ai.addTube('blackstone', [[0.02, 1.42, -0.02], [0.22, 1.66, -0.02], [0.46, 1.74, -0.02], [0.58, 1.62, -0.02], [0.47, 1.52, -0.02], [0.3, 1.46, -0.02]], 0.04, 0.02)
ai.addTube('crying_obsidian', [[0.04, 1.44, -0.05], [0.22, 1.67, -0.05], [0.46, 1.75, -0.05], [0.55, 1.64, -0.05]], 0.015, 0.008) // bright edge
ai.groupEnd()
ai.animate('blade', 'swing', 0, 1.5, 0, 'z', 0.5, 3)

// void core inside the crescent (glows and pulses)
ai.groupStart('core')
ai.addSphere('obsidian', 0.24, 1.52, 0, 0.09)
ai.addSphere('amethyst_block', 0.24, 1.52, 0, 0.05)
ai.groupEnd()
ai.glow('core')
ai.animate('core', 'pulse', 0.24, 1.52, 0, 'y', 0.6, 0.1)

// effects
ai.hitEffect('portal', '#9b30ff', 10, 'converge', 2)
ai.idleEffect('portal', '#3a0f5c', 3)
ai.aura('#4b0082', 0.5)