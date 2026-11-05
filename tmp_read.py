from pathlib import Path
for n in ['MainActivity.kt','QuizLockActivity.kt']:
 p=Path('app/src/main/java/com/huiyang/quizlocktv')/n
 print('===== '+n+' =====')
 print(p.read_text(encoding='utf-8'))
