import { NavigationContainer } from '@react-navigation/native'
import { createNativeStackNavigator } from '@react-navigation/native-stack'
import { StatusBar } from 'expo-status-bar'
import { useEffect, useState } from 'react'
import { ActivityIndicator, Button, FlatList, SafeAreaView, ScrollView, Text, TextInput, View } from 'react-native'
import { api, getToken, setToken } from './src/api'

type AnalysisJob = {
  jobId: string
  repository: string
  status: string
  findingCount?: number
  errorMessage?: string
}

type Finding = {
  id: number
  vulnerabilityType: string
  severity: string
  filePath?: string
  lineNumber?: number
  description?: string
  remediation?: string
  aiExplanation?: string
}

type Results = { jobId: string; status: string; findingCount?: number; findings: Finding[] }

const Stack = createNativeStackNavigator()

function LoginScreen({
  navigation,
  onAuthed,
}: {
  navigation: { replace: (s: string) => void }
  onAuthed: () => void
}) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function submit(register: boolean) {
    setError(null)
    setLoading(true)
    try {
      const path = register ? '/api/auth/register' : '/api/auth/login'
      const data = await api<{ token: string }>(path, {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      await setToken(data.token)
      onAuthed()
      navigation.replace('Home')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Auth failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, padding: 20, justifyContent: 'center' }}>
      <Text style={{ fontSize: 28, fontWeight: '700', marginBottom: 8 }}>SecureAI</Text>
      <Text style={{ color: '#64748b', marginBottom: 24 }}>Mobile security analysis client</Text>
      <TextInput placeholder="Email" value={email} onChangeText={setEmail} autoCapitalize="none" style={{ borderWidth: 1, borderColor: '#cbd5e1', borderRadius: 8, padding: 12, marginBottom: 12 }} />
      <TextInput placeholder="Password" value={password} onChangeText={setPassword} secureTextEntry style={{ borderWidth: 1, borderColor: '#cbd5e1', borderRadius: 8, padding: 12, marginBottom: 12 }} />
      {error && <Text style={{ color: '#b91c1c', marginBottom: 12 }}>{error}</Text>}
      <Button title={loading ? 'Please wait…' : 'Sign in'} onPress={() => submit(false)} disabled={loading} />
      <View style={{ height: 8 }} />
      <Button title="Register" onPress={() => submit(true)} disabled={loading} />
    </SafeAreaView>
  )
}

function HomeScreen({ navigation }: { navigation: { navigate: (s: string, p?: object) => void } }) {
  const [repository, setRepository] = useState('samples')
  const [jobs, setJobs] = useState<AnalysisJob[]>([])
  const [loading, setLoading] = useState(false)

  async function refresh() {
    const list = await api<AnalysisJob[]>('/api/analysis')
    setJobs(list)
  }

  useEffect(() => {
    refresh().catch(() => {})
  }, [])

  async function submit() {
    setLoading(true)
    try {
      const job = await api<AnalysisJob>('/api/analysis', {
        method: 'POST',
        body: JSON.stringify({ repository }),
      })
      navigation.navigate('Job', { jobId: job.jobId })
    } finally {
      setLoading(false)
    }
  }

  return (
    <SafeAreaView style={{ flex: 1, padding: 16 }}>
      <Text style={{ fontSize: 22, fontWeight: '600', marginBottom: 12 }}>New analysis</Text>
      <TextInput value={repository} onChangeText={setRepository} placeholder="Repository URL or samples" style={{ borderWidth: 1, borderColor: '#cbd5e1', borderRadius: 8, padding: 12, marginBottom: 12 }} />
      <Button title={loading ? 'Submitting…' : 'Analyze'} onPress={submit} disabled={loading} />
      <Text style={{ fontSize: 18, fontWeight: '600', marginTop: 24, marginBottom: 8 }}>Recent jobs</Text>
      <FlatList
        data={jobs}
        keyExtractor={(item) => item.jobId}
        renderItem={({ item }) => (
          <View style={{ paddingVertical: 10, borderBottomWidth: 1, borderColor: '#e2e8f0' }}>
            <Text onPress={() => navigation.navigate('Job', { jobId: item.jobId })} style={{ fontWeight: '500' }}>
              {item.jobId.slice(0, 8)}… — {item.status}
            </Text>
            <Text style={{ color: '#64748b', fontSize: 12 }}>{item.repository}</Text>
          </View>
        )}
      />
    </SafeAreaView>
  )
}

function JobScreen({ route }: { route: { params: { jobId: string } } }) {
  const { jobId } = route.params
  const [job, setJob] = useState<AnalysisJob | null>(null)
  const [results, setResults] = useState<Results | null>(null)

  useEffect(() => {
    const interval = setInterval(async () => {
      try {
        const j = await api<AnalysisJob>(`/api/analysis/${jobId}`)
        setJob(j)
        if (j.status === 'COMPLETED' || j.status === 'FAILED') {
          const r = await api<Results>(`/api/analysis/${jobId}/results`)
          setResults(r)
          clearInterval(interval)
        }
      } catch {
        /* retry */
      }
    }, 3000)
    return () => clearInterval(interval)
  }, [jobId])

  return (
    <SafeAreaView style={{ flex: 1, padding: 16 }}>
      <Text style={{ fontFamily: 'monospace', fontSize: 12, marginBottom: 8 }}>{jobId}</Text>
      <Text style={{ fontSize: 18, fontWeight: '600', marginBottom: 12 }}>Status: {job?.status ?? '…'}</Text>
      {job?.errorMessage && <Text style={{ color: '#b91c1c', marginBottom: 12 }}>{job.errorMessage}</Text>}
      <ScrollView>
        {(results?.findings ?? []).map((f) => (
          <View key={f.id} style={{ marginBottom: 16, padding: 12, backgroundColor: '#f8fafc', borderRadius: 8 }}>
            <Text style={{ fontWeight: '600' }}>
              [{f.severity}] {f.vulnerabilityType}
            </Text>
            <Text style={{ fontSize: 12, color: '#64748b' }}>
              {f.filePath}:{f.lineNumber}
            </Text>
            <Text style={{ marginTop: 6 }}>{f.description}</Text>
            {f.aiExplanation && <Text style={{ marginTop: 6, color: '#0f766e' }}>AI: {f.aiExplanation}</Text>}
            <Text style={{ marginTop: 6 }}>Fix: {f.remediation}</Text>
          </View>
        ))}
      </ScrollView>
    </SafeAreaView>
  )
}

export default function App() {
  const [ready, setReady] = useState(false)
  const [authed, setAuthed] = useState(false)

  useEffect(() => {
    getToken().then((t) => {
      setAuthed(Boolean(t))
      setReady(true)
    })
  }, [])

  if (!ready) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator />
      </View>
    )
  }

  return (
    <NavigationContainer>
      <StatusBar style="auto" />
      <Stack.Navigator screenOptions={{ headerShown: true }}>
        {!authed ? (
          <Stack.Screen name="Login" options={{ headerShown: false }}>
            {(props) => <LoginScreen {...props} onAuthed={() => setAuthed(true)} />}
          </Stack.Screen>
        ) : (
          <>
            <Stack.Screen name="Home" component={HomeScreen} options={{ title: 'SecureAI' }} />
            <Stack.Screen name="Job" component={JobScreen} options={{ title: 'Analysis Job' }} />
          </>
        )}
      </Stack.Navigator>
    </NavigationContainer>
  )
}
