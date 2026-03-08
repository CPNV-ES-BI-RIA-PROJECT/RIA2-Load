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
           '20250201T080000Z',
           '20250303T090000',
           '20250303T150000',
           'Work session',
           '[acme.ch] Development session',
           'BUSINESS',
           'contact@acme.ch',
           'john.doe@mycompany.com',
           'https://maps.google.com/?q=46.2044,6.1432',
           'Europe/Bern'
       );