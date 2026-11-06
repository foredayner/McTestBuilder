// 김치냉장고: a low lid-top kimchi refrigerator, back at z = 0
def body = '#e9e6de'
ai.addRoundBox(body, -0.44, 0, -0.84, 0.88, 0.86, 0.8, 0.03)        // body
ai.addPart('#c8b8a8', -0.44, 0.82, -0.85, 0.88, 0.05, 0.81)          // rose-gold lid line
ai.addRoundBox(body, -0.43, 0.86, -0.83, 0.42, 0.04, 0.78, 0.015)    // two lids on top
ai.addRoundBox(body, 0.01, 0.86, -0.83, 0.42, 0.04, 0.78, 0.015)
ai.addCapsule('#a8a8a8', -0.36, 0.85, -0.85, -0.08, 0.85, -0.85, 0.012)   // lid handles in front
ai.addCapsule('#a8a8a8', 0.08, 0.85, -0.85, 0.36, 0.85, -0.85, 0.012)
ai.addPart('#b8a898', -0.44, 0.42, -0.85, 0.88, 0.01, 0.01)          // drawer line
ai.addCapsule('#a8a8a8', -0.2, 0.36, -0.86, 0.2, 0.36, -0.86, 0.012) // drawer handle
ai.addPart('#5a8ab8', -0.4, 0.7, -0.845, 0.1, 0.05, 0.004)           // a little blue display
ai.addPart('#d0b0c0', 0.1, 0.5, -0.845, 0.3, 0.25, 0.004)            // a printed flower pattern panel
