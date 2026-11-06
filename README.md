# Launcher Simples para os avós

Uma tela inicial (launcher) para Android feita para pessoas idosas, que **não sai
da tela padrão** e **trava as configurações que eles costumam desmontar sem querer**.

## Por que os launchers comuns "se soltam"?

Um launcher comum é só um app escolhido como "tela inicial padrão". Qualquer
coisa pode desfazer essa escolha: um app novo que também é launcher, uma
atualização do sistema, a pessoa tocar em "Sempre" na caixa errada, limpeza de
dados, modo de economia de bateria…

Este app usa outro caminho: ele vira **Dono do dispositivo** (*Device Owner*),
o mesmo recurso oficial do Android que empresas usam para gerenciar os
celulares dos funcionários. Como dono do dispositivo ele consegue:

| Problema | Como o app resolve |
|---|---|
| Launcher sai da tela padrão | Fica registrado como tela inicial **permanente** (`addPersistentPreferredActivity`). O Android não troca sozinho, e o usuário não consegue trocar |
| Baixam vírus / APK pelo navegador ou WhatsApp | Instalação **fora da Play Store bloqueada sempre** (`DISALLOW_INSTALL_UNKNOWN_SOURCES[_GLOBALLY]`) |
| Instalam porcaria pela Play Store | Interruptor no painel: **Play Store liberada ou bloqueada**, e um botão "liberar por 1 hora" |
| Desinstalam o WhatsApp sem querer | Desinstalar fica bloqueado junto com a Play Store |
| Bagunçam a tela inicial | A tela não tem edição nenhuma: não dá para arrastar, apagar nem criar atalho |
| Tiram o volume / colocam no silencioso | Um vigia em segundo plano volta o volume para o **mínimo que você escolher** na hora |
| Diminuem o brilho | Brilho travado (Android 9+) e reposto se alguém mudar |
| Desligam Wi-Fi / dados / modo avião | Configurações de rede travadas, e o Wi-Fi é religado sozinho |
| Notificações falsas de "vírus" no Chrome | Notificações e pop-ups de sites bloqueados no Chrome |
| Resetam o celular / entram em modo seguro | Bloqueado |
| Mexem nos atalhos rápidos da barra de cima | Opção de bloquear a barra inteira |

Fazer um sistema operacional próprio (ROM) **não vale a pena**: só funciona em
alguns modelos, perde atualizações de segurança, pode quebrar o banco e o
WhatsApp, e qualquer erro trava o celular. O modo "dono do dispositivo" dá
quase o mesmo controle, em qualquer Android 8 ou mais novo, sem desbloquear
nada.

## Como usar

### 1. Gerar o APK

- **Pelo GitHub:** a cada push, a aba *Actions* gera o `launcher-apk` para baixar.
- **No seu computador:** abra a pasta no Android Studio e use *Build → Build APK*,
  ou rode `./gradlew assembleDebug`. O APK fica em `app/build/outputs/apk/debug/`.

> **Importante sobre atualizações:** depois que o app vira dono do dispositivo,
> toda versão nova precisa ser assinada com a **mesma chave**. Ou você gera
> sempre do mesmo computador (o Android Studio usa a mesma chave de debug), ou
> cria uma keystore e cadastra estes *secrets* no GitHub: `KEYSTORE_BASE64`,
> `KEYSTORE_PASSWORD`, `KEY_ALIAS` e `KEY_PASSWORD`.
>
> ```bash
> keytool -genkeypair -v -keystore launcher.jks -alias launcher -keyalg RSA -keysize 2048 -validity 10000
> base64 -w0 launcher.jks   # cole o resultado no secret KEYSTORE_BASE64
> ```

### 2. Preparar o celular

O Android só deixa um app virar dono do dispositivo se **não houver nenhuma
conta** no celular (Google, Samsung, Xiaomi, WhatsApp Business não conta).

1. Faça backup do WhatsApp (Configurações → Conversas → Backup).
2. Em *Configurações → Contas*, **remova todas as contas** (Google, Samsung,
   Mi…). Elas voltam no passo 4. Se o celular não deixar remover alguma, a
   saída é restaurar de fábrica e configurar sem entrar com conta.
3. Ative as **Opções do desenvolvedor** (toque 7 vezes em *Número da versão*) e
   ligue a **Depuração USB**.
   - Xiaomi/Redmi/POCO: ligue também *Depuração USB (Configurações de segurança)*.
4. Conecte no computador com o [adb](https://developer.android.com/tools/releases/platform-tools) e rode:

```bash
adb install app-debug.apk
```

```bash
adb shell dpm set-device-owner br.com.familia.launcher/.AdminReceiver
```

   Deve aparecer `Success: Device owner set to package ...`.

5. Agora pode adicionar de volta a conta Google e restaurar o WhatsApp.
   (Se o restaurador pedir para instalar algo, libere a Play Store no painel.)

### 3. Configurar

Na tela inicial, **segure o dedo no relógio** por um segundo. Na primeira vez
ele pede para criar um **PIN**; depois disso só quem sabe o PIN entra no
*Painel da família*, onde dá para:

- liberar/bloquear a Play Store (ou liberar só por 1 hora para atualizar apps);
- travar Wi-Fi, dados e modo avião, e religar o Wi-Fi automaticamente;
- travar o brilho (fixo ou automático);
- escolher o volume mínimo;
- bloquear a barra de notificações/atalhos rápidos;
- escolher quais apps aparecem na tela inicial;
- abrir as configurações do Android para manutenção;
- trocar o PIN ou desativar toda a proteção.

## Coisas para saber

- **Play Store bloqueada também bloqueia atualizações automáticas.** Use o
  botão "liberar por 1 hora" de vez em quando (uma vez por mês, por exemplo)
  para o WhatsApp, Chrome e banco ficarem atualizados.
- **Bloquear a barra de cima** esconde também as notificações (WhatsApp etc.).
  Por isso vem desligado; teste com eles e veja o que funciona melhor.
- Com a rede travada, para conectar num Wi-Fi novo você precisa destravar no painel.
- Esqueceu o PIN? Pelo computador (com a depuração USB ligada):

  ```bash
  adb shell am broadcast -a br.com.familia.launcher.RESET_PIN -n br.com.familia.launcher/.ResetPinReceiver
  ```

  O próximo toque longo no relógio pede para criar um PIN novo. As outras configurações continuam iguais.
- Para remover o app de vez, use *Desativar proteção* no painel e depois desinstale.

## Estrutura do código

| Arquivo | O que faz |
|---|---|
| `HomeActivity.kt` | Tela inicial: relógio, status de bateria/internet e botões grandes |
| `AdminActivity.kt` | Painel da família (PIN) |
| `Policies.kt` | Todas as travas do modo dono do dispositivo |
| `GuardService.kt` | Vigia que repõe volume, Wi-Fi e brilho e encerra a liberação temporária |
| `Prefs.kt` | Configurações e PIN (guardado com hash) |
| `Apps.kt` | Lista de apps e seleção padrão (Telefone, WhatsApp, Contatos, Mensagens, Câmera, Fotos, YouTube) |
| `AdminReceiver.kt` / `BootReceiver.kt` | Reaplicam tudo ao ativar e ao ligar o celular |
