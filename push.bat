@echo off
chcp 65001 >nul

set /p commit=Commit message: 

git init
git add .
git commit -m "%commit%"
git branch -M main

git remote remove origin 2>nul
git remote add origin https://github.com/zhengjiahong88/NWMANAGER.git

git push -u origin main

pause