// 신발장: a tall entrance shoe cabinet with slippers in front, back at z = 0
def white = '#efece6'
ai.addRoundBox(white, -0.45, 0.06, -0.38, 0.9, 1.8, 0.36, 0.015)     // cabinet, on a little plinth
ai.addPart('#b8b4ac', -0.44, 0, -0.36, 0.88, 0.06, 0.32)             // plinth
ai.addPart('#c8c4bc', -0.005, 0.06, -0.385, 0.01, 1.8, 0.005)       // the two doors
ai.addPart('#a8a8a8', -0.05, 0.7, -0.39, 0.012, 0.25, 0.012)         // handles
ai.addPart('#a8a8a8', 0.038, 0.7, -0.39, 0.012, 0.25, 0.012)
ai.addPart('#c8c4bc', -0.45, 1.1, -0.385, 0.9, 0.008, 0.005)        // a shelf gap with a mirror strip
ai.addPart('#a8c8d8', -0.43, 1.12, -0.386, 0.86, 0.12, 0.004)
// slippers on the floor in front
ai.addRoundBox('#5a8ab8', -0.3, 0, -0.68, 0.12, 0.03, 0.26, 0.04)
ai.addRoundBox('#5a8ab8', -0.15, 0, -0.68, 0.12, 0.03, 0.26, 0.04)
ai.addRoundBox('#4a7aa8', -0.3, 0.03, -0.62, 0.12, 0.04, 0.1, 0.02)  // their straps
ai.addRoundBox('#4a7aa8', -0.15, 0.03, -0.62, 0.12, 0.04, 0.1, 0.02)
