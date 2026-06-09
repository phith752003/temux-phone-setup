Start-Transcript -Path "c:\Users\ADMIN\Desktop\temux-install\driver_install.log"
pnputil.exe /add-driver "c:\Users\ADMIN\Desktop\temux-install\usb-driver-dist\usb_driver\android_winusb.inf" /install
Stop-Transcript
