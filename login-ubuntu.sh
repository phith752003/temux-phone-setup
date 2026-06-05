#!/data/data/com.termux/files/usr/bin/bash
# Shortcut script to login to Ubuntu and automatically cd to the project folder

proot-distro login ubuntu --shared-tmp -- bash -c "cd /data/data/com.termux/files/home/temux-install && exec bash"
