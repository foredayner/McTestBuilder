// Large battle scythe (game/anime style), about 2.7 long
// Parts:
// 1. Long shaft: dark wood, from y=-0.2 to 2.3, with leather grip wraps near the hand
// 2. Pommel spike at the bottom
// 3. Collar at the top: dark metal ring with gold trim
// 4. Crescent blade: tapered tube sweeping to +X, curving down to a point, dark back
// 5. Bright cutting edge on the inner (lower) side
// 6. Spike on top of the shaft and a small back spike

// shaft
ai.addCapsule('dark_oak_planks', 0, -0.2, 0, 0, 2.3, 0, 0.045)
// grip wraps
ai.addCylinder('black_concrete', 0, -0.1, 0, 0.058, 0.5)
ai.addCylinder('black_concrete', 0, 1.0, 0, 0.055, 0.35)
// gold rings on the grip
ai.addCylinder('gold_block', 0, 0.42, 0, 0.065, 0.04)
ai.addCylinder('gold_block', 0, -0.12, 0, 0.065, 0.04)
// pommel
ai.addCone('iron_block', 0, -0.42, 0, 0.001, 0.06, 0.22)
ai.addSphere('gold_block', 0, -0.2, 0, 0.07)

// collar
ai.addCylinder('gray_concrete', 0, 2.15, 0, 0.09, 0.2)
ai.addCylinder('gold_block', 0, 2.12, 0, 0.105, 0.04)
ai.addCylinder('gold_block', 0, 2.34, 0, 0.105, 0.04)
ai.addRoundBox('gray_concrete', -0.13, 2.2, -0.08, 0.26, 0.1, 0.16, 0.04)

// top spike
ai.addCone('iron_block', 0, 2.36, 0, 0.07, 0.3)

// blade back (dark, thick, sweeping out to +X then curving down)
ai.addTube('gray_concrete', [[0.0, 2.32, 0], [0.4, 2.46, 0], [0.85, 2.44, 0], [1.25, 2.26, 0], [1.5, 1.95, 0]], 0.13, 0.025)
// blade body lighter steel layer slightly in front
ai.addTube('iron_block', [[0.15, 2.3, -0.02], [0.5, 2.39, -0.02], [0.9, 2.34, -0.02], [1.25, 2.16, -0.02], [1.47, 1.95, -0.02]], 0.095, 0.015)
// bright cutting edge on the inner lower side
ai.addTube('white_concrete', [[0.25, 2.24, -0.03], [0.6, 2.3, -0.03], [0.95, 2.22, -0.03], [1.28, 2.05, -0.03], [1.47, 1.95, -0.03]], 0.04, 0.01)
// blade root block connecting to collar
ai.addRoundBox('gray_concrete', 0.0, 2.2, -0.06, 0.25, 0.22, 0.12, 0.04)

// decorative spine ridge along the back
ai.addTube('black_concrete', [[0.1, 2.46, 0], [0.5, 2.56, 0], [0.9, 2.52, 0], [1.28, 2.33, 0]], 0.04, 0.015)

// back spike opposite the blade
ai.addCone('gray_concrete', -0.05, 2.25, 0, 0.001, 0.06, 0.01)
ai.addCapsule('gray_concrete', -0.05, 2.28, 0, -0.35, 2.4, 0, 0.06, 0.015)

// red gem accent in collar front
ai.addSphere('red_concrete', 0, 2.25, -0.09, 0.05)