// 성스러운 빛의 장검 (holy light longsword), about 1.5 long; hand at (0, 0, 0), blade up (+Y), wide side facing -Z

// grip: white leather wrap
ai.addCapsule('white_terracotta', 0, -0.1, 0, 0, 0.22, 0, 0.035)
// gold wrap rings
ai.addRoundBox('gold_block', -0.045, 0.02, -0.045, 0.09, 0.02, 0.09, 0.01)
ai.addRoundBox('gold_block', -0.045, 0.12, -0.045, 0.09, 0.02, 0.09, 0.01)
// pommel
ai.addSphere('gold_block', 0, -0.14, 0, 0.055)

// cross guard with rounded wing tips
ai.addRoundBox('gold_block', -0.22, 0.22, -0.05, 0.44, 0.07, 0.1, 0.03)
ai.addSphere('gold_block', -0.22, 0.255, -0.05, 0.04)
ai.addSphere('gold_block', 0.22, 0.255, -0.05, 0.04)
// central holy gem
ai.addSphere('sea_lantern', 0, 0.255, -0.06, 0.04)

// blade: quartz core
ai.addRoundBox('quartz_block', -0.08, 0.29, -0.025, 0.16, 1.0, 0.05, 0.01)
// gold edge strips on both sides
ai.addRoundBox('gold_block', -0.088, 0.29, -0.03, 0.02, 0.98, 0.06, 0.005)
ai.addRoundBox('gold_block', 0.068, 0.29, -0.03, 0.02, 0.98, 0.06, 0.005)
// central fuller (light groove)
ai.addRoundBox('white_concrete', -0.012, 0.36, -0.03, 0.024, 0.85, 0.06, 0)
// pointed tip
ai.addEllipsoid('quartz_block', 0, 1.3, 0, 0.08, 0.2, 0.025)

// hit effect: light particles bursting outward in a sphere from one point
ai.hitEffect('glow', '#fff3b0', 10, 'expand', 1.5)

// held effect: golden halo wrapping the whole sword
ai.aura('#ffd700', 0.6)