import configparser
import os
import sys

def main():
    if len(sys.argv) < 3:
        print("Usage: python update_client_id.py <client_id> <client_secret>")
        sys.exit(1)
        
    client_id = sys.argv[1]
    client_secret = sys.argv[2]
    
    path = os.path.expanduser('~/.config/rclone/rclone.conf')
    if not os.path.exists(path):
        print(f"Error: Config file not found at {path}")
        sys.exit(1)
        
    config = configparser.ConfigParser(interpolation=None)
    config.read(path)
    
    updated = False
    for section in ['gdrive_cam1', 'gdrive_cam2']:
        if section in config:
            config.set(section, 'client_id', client_id)
            config.set(section, 'client_secret', client_secret)
            updated = True
            
    if updated:
        with open(path, 'w') as f:
            config.write(f)
        print("SUCCESS: client_id and client_secret updated for gdrive_cam1 and gdrive_cam2!")
    else:
        print("Warning: Neither gdrive_cam1 nor gdrive_cam2 sections found in rclone.conf.")

if __name__ == '__main__':
    main()
