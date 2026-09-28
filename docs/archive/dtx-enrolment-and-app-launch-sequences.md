## Enrolment (NHS Instigated - Individual Patient)

This diagram shows the high level sequence of events and communication involved in enroling a patient on a DTx
```mermaid
  sequenceDiagram
    actor Patient
    NHS->>DTx: Patient Enrolment (NHS Number, Names, DoB, Email Address, other required fields)
    DTx->>DTx: Validation and Creation of Patient Account
    DTx->>NHS: Acknowledge Enrolment and Account Creation (MI)
    NHS->>Patient: Notification of Enrolment
    DTx-->>Patient: Welcome Email / Onboarding Instruction
```

## Enrolment (NHS Instigated - Bulk Patient - Immediate Account Creation)

This diagram shows the high level sequence of events and communication involved in enroling a patient on a DTx in bulk.  The accounts would be created upon consumption of the bulk enrolment and sit idle in the DTx until the patient starts to make use of it.
```mermaid
  sequenceDiagram
    actor Patient
    NHS->>DTx: Bulk Patient Enrolment (NHS Number, Names, DoB, Email Address, other required fields)
    NHS->>DTx:
    NHS->>DTx:
    loop For each patient
      DTx->>DTx: Validation and Creation of Patient Account
      DTx->>NHS: Acknowledge Enrolment and Account Creation (MI)
      NHS->>Patient: Notification of Enrolment
      DTx-->>Patient: Welcome Email / Onboarding Instruction
    end
```

## Enrolment (NHS Instigated - Bulk Patient - Deferred Account Creation)

This diagram shows the high level sequence of events and communication involved in enroling a patient on a DTx.  The patient account is only created within the DTx at the time the patient first attempts to make use of it. Auth Handoff would be by the means described in the App Launch sequnces below
```mermaid
  sequenceDiagram
    actor Patient
    NHS->>Patient: Notification of Enrolment
    Patient->>NHS: Access DTx
    NHS->>DTx: Patient Enrolment (NHS Number, Names, DoB, Email Address, other required fields)
    DTx->>DTx: Validation and Creation of Patient Account
    DTx->>NHS: Acknowledge Enrolment and Account Creation (MI)
    rect rgb(180,180,228)
      DTx-->>Patient: Auth Handoff Request
      Patient-->>NHS: Auth Handoff Request
      NHS-->>Patient: Auth Handoff Response
      Patient-->>DTx: Auth Handoff Response
    end
    DTx->>Patient: Session and App Content
    DTx-->>Patient: Welcome Email / Onboarding Instruction
    
```

## DTx App Launch - Pre-installed

This is the sequence for when a patient already has the DTx app installed and they click on a link in the NHS App to launch the DTx App. It essentially uses a native equivalent of OAuth by opening links to the native apps with OAuth parameters, instead of links to the websites with OAuth parameters.  Initial investigation has shown this should be possible.
```mermaid
  sequenceDiagram
  actor Patient
    Patient ->> NHS App: Open NHS App / Login
    NHS App ->> NHS DTx App: Request DTx List "Connect Apps"
    NHS DTx App ->> NHS App: Links for "Connect Apps"
    NHS App ->> Patient: Session Tokens and App Content (Links to relevant DTx)
    Patient->>NHS DTx App: Clink link to DTx 
    NHS DTx App ->> NHS DTx App: Generate - asserted_login_identity
    NHS DTx App ->> Patient: DTx Universal Link with asserted_login_identity
    Patient ->> DTx: Open DTx with asserted_login_identity parameter
    DTx->>DTx: OAuth 2 Start Authorisation Request - generate redirect URL including asserted_login_identity
    Note right of DTx: Example<br/> HTTP Request<br/> GET https://dtx-authorisation-service-api/nhs-login/redirectWithIdentity?asserted_login_identity=<SSO JWT> <br/><br/>HTTP Response <br/>302 Found<br/> Location: https://nhs-app-universal-link/authorize?<br/>response_type=code&<br/>client_id=<clientId>&<br/>redirect_uri=https://dtx-universal-link/redirect&<br/>scope=email openid profile&<br/>state=<state as per spec>&<br/>asserted_login_identity=<SSO JWT>&<br/>prompt="none"|blank&<br/>vtr=%5B%22<VTR/VOT VALUE>%22%5D
    DTx->>Patient: NHS App Universal Link with additional asserted_login_identity parameter, vtr parameter and prompt parameter
    Patient->>NHS App: NHS App Universal Link with asserted_login_identity parameter, vtr parameter and prompt parameter

    NHS App->>NHS Login: Retrieve OAuth Exchange code based on asserted_login_identity
    NHS Login->>Patient: Redirect with OAuth Exchange Code (to DTx Universal Link)
    Note right of NHS App: Example<br/> https://dtx-universal-link/exchange?<br/>code=<exchange-code>&<br/>state=<state as per auth request>
    Patient->>DTx: DTx Universal Link with OAuth Exchange Code
    DTx->>NHS Login: Exchange OAuth code for Session Tokens (checking state matches authorisation request)
    NHS Login->>DTx: Session Tokens
    DTx->>Patient: Redirect to Landing page with Session Tokens and App Content
```

## DTx App Launch - SSO through Installation

This is the sequence for when a patient does not have the DTx app installed and they click on a link in the NHS App to launch the DTx App. 
It relies on a custom app page, which should theoretically be able to launch an app with a custom parameter, this being one which initiates the SSO style communication as above.
```mermaid
  sequenceDiagram
  actor Patient
    Patient ->> NHS App: Open NHS App / Login
    NHS App ->> NHS DTx App: Request DTx List "Connect Apps"
    NHS DTx App ->> NHS App: Links for "Connect Apps"
    NHS App ->> Patient: Session Tokens and App Content (Links to relevant DTx)
    Patient->>AppStore: DTx Custom Page Link (when this app starts it requests SSO Start)
    AppStore->>Patient: App from Custom Page
    Patient ->> DTx: Launch (when this app starts it requests SSO Start)
    DTx->>Patient: Universal Link of NHS App with DTx identifier
    Patient->>NHS App: SSO Request with DTx identifier
    NHS App->>NHS DTx App: Request SSO with DTx identifier
    NHS DTx App ->> NHS DTx App: Generate - asserted_login_identity
    NHS DTx App ->> Patient: DTx Universal Link with asserted_login_identity
    Patient ->> DTx: Open DTx with asserted_login_identity parameter
    DTx->>DTx: OAuth 2 Start Authorisation Request - generate redirect URL including asserted_login_identity
    Note right of DTx: Example<br/> HTTP Request<br/> GET https://dtx-authorisation-service-api/nhs-login/redirectWithIdentity?asserted_login_identity=<SSO JWT> <br/><br/>HTTP Response <br/>302 Found<br/> Location: https://nhs-app-universal-link/authorize?<br/>response_type=code&<br/>client_id=<clientId>&<br/>redirect_uri=https://dtx-universal-link/redirect&<br/>scope=email openid profile&<br/>state=<state as per spec>&<br/>asserted_login_identity=<SSO JWT>&<br/>prompt="none"|blank&<br/>vtr=%5B%22<VTR/VOT VALUE>%22%5D
    DTx->>Patient: NHS App Universal Link with additional asserted_login_identity parameter, vtr parameter and prompt parameter
    Patient->>NHS App: NHS App Universal Link with asserted_login_identity parameter, vtr parameter and prompt parameter

    NHS App->>NHS Login: Retrieve OAuth Exchange code based on asserted_login_identity
    NHS Login->>Patient: Redirect with OAuth Exchange Code (to DTx Universal Link)
    Note right of NHS App: Example<br/> https://dtx-universal-link/exchange?<br/>code=<exchange-code>&<br/>state=<state as per auth request>
    Patient->>DTx: DTx Universal Link with OAuth Exchange Code
    DTx->>NHS Login: Exchange OAuth code for Session Tokens (checking state matches authorisation request)
    NHS Login->>DTx: Session Tokens
    DTx->>Patient: Redirect to Landing page with Session Tokens and App Content
```
