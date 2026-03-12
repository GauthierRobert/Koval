import { ExpoConfig, ConfigContext } from 'expo/config';
import * as fs from 'fs';
import * as path from 'path';

export default ({ config }: ConfigContext): ExpoConfig => {
  const googleServicesFile = path.resolve(__dirname, 'google-services.json');
  const hasGoogleServices = fs.existsSync(googleServicesFile);

  return {
    ...config,
    name: 'Koval',
    slug: 'koval',
    scheme: 'koval',
    version: '1.0.0',
    orientation: 'portrait',
    icon: './assets/icon.png',
    userInterfaceStyle: 'dark',
    newArchEnabled: false,
    splash: {
      image: './assets/splash-icon.png',
      resizeMode: 'contain',
      backgroundColor: '#0f0f0f',
    },
    ios: {
      supportsTablet: true,
      bundleIdentifier: 'com.koval.trainingplanner',
    },
    android: {
      adaptiveIcon: {
        foregroundImage: './assets/adaptive-icon.png',
        backgroundColor: '#0f0f0f',
      },
      edgeToEdgeEnabled: true,
      package: 'com.koval.trainingplanner',
      ...(hasGoogleServices && { googleServicesFile: './google-services.json' }),
      intentFilters: [
        {
          action: 'VIEW',
          autoVerify: true,
          data: [{ scheme: 'koval' }],
          category: ['BROWSABLE', 'DEFAULT'],
        },
      ],
    },
    web: {
      favicon: './assets/favicon.png',
    },
    plugins: [
      'expo-router',
      'expo-secure-store',
      [
        'expo-notifications',
        {
          icon: './assets/adaptive-icon.png',
          color: '#00c2ff',
        },
      ],
    ],
  };
};
