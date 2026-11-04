# Patient Flow — Notification to Signed-In DTx

> **The App Store breaks the chain.** Native device navigation into the App Store does not
> carry `asserted_login_identity` through to the installed app. The installed
> DTx therefore starts with no identity and must initiate the handoff itself, returning the patient to the NHS App after install.
>
> **The mechanism is user-agent agnostic.** `asserted_login_identity` is a signed JWT carried
> as a parameter on the authorisation request from RP1 to RP2. NHS Login defines it at that
> level and draws no distinction between a browser and an installed app, so the mechanism is
> unchanged either way. Ref: [NHS Login — Single Sign
> On](https://nhsconnect.github.io/nhslogin/single-sign-on/)
> ([sequence](https://raw.githubusercontent.com/nhsconnect/nhslogin/main/src/images/SequenceDiagram_smaller.png)).
>
> **RP1 to RP2 relationships.**:
>
> | RP1 | RP2 | Where |
> | --- | --- | --- |
> | NHS App | NHS DTx | Within the NHS App, navigating to Connected Apps |
> | NHS DTx | DTx App | Between NHS DTx and a digital therapeutic |

NHS Notify message through to a signed-in DTx session, for a patient without the DTx app
installed.

Participant names follow the existing sequences; `NHS DTx App` is the NHS-side DTx service,
not a patient-facing app.

## Steps

1. NHS Notify delivers the enrolment notification to the **Messages** tab of the NHS App.
2. Patient reads the message and taps its link — in-app navigation to the Connected Apps
   **detail page** for the therapeutic.
3. Patient taps **Install app**.
4. Patient is taken to the App Store custom product page and installs the DTx.
5. Patient opens the DTx. It has no session.
6. DTx directs the patient into the NHS App at a dedicated deep link — offered, or automatic.
7. NHS App returns the patient to the DTx with an `asserted_login_identity` — after an OAuth
   consent screen, or automatic where consent exists.
8. DTx completes the OAuth exchange and signs the patient in.

## Sequence

```mermaid
  sequenceDiagram
  actor Patient
    NHS Notify ->> NHS App: Enrolment notification (surfaces in the Messages tab)
    Patient ->> NHS App: Open NHS App / Login
    Patient ->> NHS App: Open Messages tab, read enrolment message
    Patient ->> NHS App: Tap link in message (in-app navigation)
    NHS App ->> NHS DTx App: Request DTx detail (DTx identifier)
    NHS DTx App ->> NHS App: DTx detail content and install link
    NHS App ->> Patient: Connected Apps detail page for the DTx
    Patient ->> NHS App: Tap "Install app"

    rect rgb(228,214,180)
      Note over NHS App,AppStore: Today - hand off to the App Store.<br/>Future - overlaid installation, keeping the patient in the NHS App. Unverified.
      NHS App ->> AppStore: DTx Custom Product Page Link
      AppStore ->> Patient: App from Custom Product Page
      Patient ->> Patient: Install DTx
    end

    Patient ->> DTx: Open DTx (no session)

    rect rgb(180,180,228)
      Note over DTx,NHS App: Handoff 1 - DTx returns the patient to the NHS App
      alt Offered
        DTx ->> Patient: Prompt - "Sign in with the NHS App"
        Patient ->> DTx: Accept
      else Automatic
        Note over DTx: No prompt - redirect immediately
      end
      DTx ->> Patient: NHS App Universal Link with DTx identifier
      Patient ->> NHS App: SSO Request with DTx identifier
    end

    rect rgb(180,180,228)
      Note over NHS App,DTx: Handoff 2 - NHS App returns the patient to the DTx with an asserted identity
      alt Consent not yet given (first time)
        NHS App ->> Patient: OAuth data agreement / scopes screen
        Patient ->> NHS App: Agree to share data
      else Consent already given
        Note over NHS App: No prompt - proceed
      end
      NHS App ->> NHS DTx App: Request SSO with DTx identifier
      NHS DTx App ->> NHS DTx App: Generate - asserted_login_identity
      NHS DTx App ->> Patient: DTx Universal Link with asserted_login_identity
      Patient ->> DTx: Open DTx with asserted_login_identity parameter
    end

    DTx ->> NHS Login: OAuth 2 Authorisation Request carrying asserted_login_identity, vtr and prompt
    Note right of DTx: Example<br/> GET https://auth.login.nhs.uk/authorize?<br/>response_type=code&<br/>client_id=<clientId>&<br/>redirect_uri=https://dtx-universal-link/redirect&<br/>scope=email openid profile&<br/>state=<state as per spec>&<br/>asserted_login_identity=<SSO JWT>&<br/>prompt="none"|blank&<br/>vtr=%5B%22<VTR/VOT VALUE>%22%5D
    NHS Login ->> Patient: Redirect with OAuth Exchange Code (to DTx Universal Link)
    Note right of NHS Login: Example<br/> https://dtx-universal-link/exchange?<br/>code=<exchange-code>&<br/>state=<state as per auth request>
    Patient ->> DTx: DTx Universal Link with OAuth Exchange Code
    DTx ->> NHS Login: Exchange OAuth code for Session Tokens (checking state matches authorisation request)
    NHS Login ->> DTx: Session Tokens
    DTx ->> Patient: Signed in - Landing page with Session Tokens and App Content
```

## Open

- **Agreement-to-share screen.** NHS Login asks the patient user to confirm their agreement to share information. If it is an NHS Login feature: can it be overridden, and
  does `asserted_login_identity` survive the NHS Login flow? Assumption is that it does not
  carry through, which would break the handoff on first run, when consent has not been given.
  Unverified.
- **Offer vs automatic.** Both handoffs are drawn with the choice explicit. Which applies,
  and whether it varies by first versus subsequent launch, is undecided.
- **Overlaid installation.** Future state replaces the App Store handoff with an in-app
  overlay (iOS `SKOverlay`, Android equivalent).
- **Message link target.** Whether the NHS App supports linking a Notify message to a
  specific DTx detail page is unconfirmed but is assumed to be the case.
- **Notify channels.** Only the NHS App message channel is drawn. SMS and email are not
  modelled.
