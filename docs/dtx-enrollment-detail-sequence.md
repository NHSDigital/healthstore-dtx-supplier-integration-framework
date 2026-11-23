## Detailed Sequence of data flow for deferred creation of accounts
This sequence shows the data flow when account creation within the dtx is postponed until after explicit consent is provided byt he patient.  This proposal will allow the NHS login consent to share data with the dtx, to be the consent for this, as all data required to create the account can be gained from the NHS login claims (with consent drivien by the scope requested) with only the healthstore_request_id as the only additional information required at the point in time the patient has clicked the link to the DTx.

```mermaid
sequenceDiagram
    box NHS Eco System
        participant HS as HealthStore
        participant NHS App
        participant nhslogin as NHS Login
        participant RS as Reporting Service
    end
    box DTx
        participant dtxAuth as DTx (Authentication)
        participant dtxUser as DTx (Authorisation)
        participant eventFwder as HealthStore Gateway
    end

    HS->>eventFwder: New Cohort
    eventFwder->>HS: Task Accepted
    loop registrations
        eventFwder->>HS: PAGE registration
        HS->>eventFwder: healthstore_request_id and ODS code
        Note over eventFwder: Store healthstore_request_id and ODS Code
        eventFwder->>HS: accepted
    end
    HS->>NHS App: Show DTx
    Note over NHS App: Select DTx
    NHS App->>nhslogin: Generate asserted_login_identity
    nhslogin->>NHS App: JWT (asserted_login_identity)
    NHS App->>dtxAuth: JWT (asserted_login_identity + healthstore_request_id)
    Note over dtxAuth: Store healthstore_request_id cookie
    dtxAuth->>nhslogin: JWT (asserted_login_identity) scopes (openid, email, profile, profile_extended, gp_registration_details)
    alt NOT Consented
    nhslogin->>nhslogin: Re-direct to CONSENT
    Note over nhslogin: No Active Session
    nhslogin->>nhslogin: Re-direct to LOGIN
    Note over nhslogin: User logs in and consents
    end
    nhslogin->>nhslogin: Generate OIDC Exchange Code
    nhslogin->>dtxAuth: Exchange Code
    Note over dtxAuth: Exchange Code for Session    

    Note over dtxAuth: Get healthstore_request_id cookie
    dtxAuth->>dtxUser: Get User (healthstore_request_id)

    alt NOT Registered
        Note over dtxUser: Register User (healthstore_request_id)
    end
    dtxUser->>dtxAuth: OK
    dtxUser->>eventFwder: UserEvent (healthstore_request_id)
    eventFwder->>RS: Registered (healthstore_request_id)
    Note over dtxAuth: Logged In 
```