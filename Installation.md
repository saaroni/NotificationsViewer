# 1. Install Java
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk unzip wget

# 2. Download command line tools (replace URL with the one from the website)
wget https://dl.google.com/android/repository/commandlinetools-linux-XXXXXXX_latest.zip

# 3. Set up the directory structure correctly
mkdir -p $HOME/Android/cmdline-tools
unzip commandlinetools-linux-*.zip -d $HOME/Android/cmdline-tools
mv $HOME/Android/cmdline-tools/cmdline-tools $HOME/Android/cmdline-tools/latest

# 4. Set environment variables
export ANDROID_HOME=$HOME/Android
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# 5. Accept licenses & install SDK
yes | sdkmanager --licenses
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

# 6. Save env vars permanently
echo 'export ANDROID_HOME=$HOME/Android' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc
source ~/.bashrc