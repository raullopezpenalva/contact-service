

The MVP must be:
- Small.
- Functional.
- Deployable.
- Professional.
- Without overengineering.

## The MVP will include:

### 1. Message Reception
- Public REST endpoint.
- Receives:
  - email
  - subject
  - content
- Validates data.
- Returns correct response.

## 2. Persistence

- Saves the message in the database.
- Assigns initial state: NEW

## 3. Notification

- Sends a simple notification to the owner.
- Can be via SMTP email.
- Does not block the user response.

## 4. Basic Administration

- Protected endpoint.
- Allows:
  - Listing messages.
  - Viewing details.
  - Changing status (NEW -> READ -> ARCHIVED)
- Simple authentication.

## The MVP will NOT include:

- Attachments.
- Admin panel with UI.
- Complex roles.
- JWT.
- Sophisticated rate limiting.
- Advanced captcha.
- Additional microservices.
- Event-driven architecture.
- Dashboard.
- Complex e2e tests.
- Queues.
- Integration with LinkedIn or newsletter.
- Physical deletion of messages.

## MVP Success Criteria

The MVP will be complete when:

- A user submits a form.
- The backend saves it.
- You receive a notification.
- You can query the message from the admin endpoint.
- It is deployed in a real environment.