import {Redirect} from 'expo-router';
import {useAuth} from '../hooks/useAuth';
import {ActivityIndicator, View} from 'react-native';
import {theme} from '../constants/theme';

export default function Index() {
  const {user, loading} = useAuth();

  if (loading) {
    return (
      <View style={{flex: 1, backgroundColor: theme.colors.background, alignItems: 'center', justifyContent: 'center'}}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
      </View>
    );
  }

  return <Redirect href={user ? '/(tabs)/chat' : '/login'} />;
}
