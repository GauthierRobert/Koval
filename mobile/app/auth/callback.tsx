import {useEffect} from 'react';
import {ActivityIndicator, View} from 'react-native';
import * as WebBrowser from 'expo-web-browser';
import {theme} from '../../constants/theme';

/**
 * This route handles the `koval://auth/callback` deep link.
 *
 * Normally `WebBrowser.openAuthSessionAsync` intercepts the redirect,
 * but on some devices/OS versions the deep link reaches Expo Router instead.
 * Calling `maybeCompleteAuthSession` here ensures the browser dismisses and
 * the auth flow in authService completes correctly.
 */
export default function AuthCallbackScreen() {
  useEffect(() => {
    WebBrowser.maybeCompleteAuthSession();
  }, []);

  return (
    <View style={{flex: 1, backgroundColor: theme.colors.background, alignItems: 'center', justifyContent: 'center'}}>
      <ActivityIndicator size="large" color={theme.colors.primary} />
    </View>
  );
}
