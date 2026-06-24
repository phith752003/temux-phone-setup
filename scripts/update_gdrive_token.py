import configparser
import os
import sys

def main():
    if len(sys.argv) < 3:
        print("Usage: python update_gdrive_token.py <remote_name> <token_json>")
        sys.exit(1)
        
    remote_name = sys.argv[1]
    token_json = sys.argv[2]
    
    path = os.path.expanduser('~/.config/rclone/rclone.conf')
    if not os.path.exists(path):
        print(f"Error: Config file not found at {path}")
        sys.exit(1)
        
    config = configparser.ConfigParser(interpolation=None)
    config.read(path)
    
    if remote_name not in config:
        print(f"Error: Remote '{remote_name}' not found in rclone.conf")
        sys.exit(1)
        
    config.set(remote_name, 'token', token_json)
    
    with open(path, 'w') as f:
        config.write(f)
    print(f"SUCCESS: Token updated for {remote_name}!")

if __name__ == '__main__':
    main()
