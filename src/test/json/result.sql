INSERT INTO events (
    uid,
    dtstamp,
    dtstart,
    dtend,
    summary,
    description,
    categories,
    organizer,
    attendee,
    location,
    timezone
)
VALUES (
           'john-20250303@mycompany.com',
           '2025-02-01 08:00:00',
           '2025-03-03 09:00:00',
           '2025-03-03 15:00:00',
           'Work session',
           '[acme.ch] Development session',
           'BUSINESS',
           'contact@acme.ch',
           'john.doe@mycompany.com',
           'https://maps.google.com/?q=46.2044,6.1432',
           'Europe/Bern'
       );
