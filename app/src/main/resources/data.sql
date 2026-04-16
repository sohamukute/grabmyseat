INSERT INTO venue (name, city)
SELECT v.name, v.city
FROM (VALUES
        ('NCPA Jamshed Bhabha Theatre', 'Mumbai'),
        ('Siri Fort Auditorium', 'Delhi'),
        ('Chowdiah Memorial Hall', 'Bengaluru'),
        ('Balgandharva Rangmandir', 'Pune')) AS v (name, city)
WHERE NOT EXISTS (SELECT 1 FROM venue x WHERE x.name = v.name);

INSERT INTO area (venue_id, name, rank)
SELECT v.id, a.name, a.rank
FROM venue v, (VALUES ('Stalls Front', 1), ('Stalls Rear', 2), ('Balcony', 3)) AS a (name, rank)
WHERE NOT EXISTS (SELECT 1 FROM area x WHERE x.venue_id = v.id AND x.name = a.name);

INSERT INTO seat (area_id, row_label, row_rank, number)
SELECT a.id, chr(64 + r), r, n
FROM area a, generate_series(1, 10) r, generate_series(1, 24) n
WHERE a.name = 'Stalls Front'
AND NOT EXISTS (SELECT 1 FROM seat s WHERE s.area_id = a.id);

INSERT INTO seat (area_id, row_label, row_rank, number)
SELECT a.id, chr(64 + r), r, n
FROM area a, generate_series(1, 12) r, generate_series(1, 24) n
WHERE a.name = 'Stalls Rear'
AND NOT EXISTS (SELECT 1 FROM seat s WHERE s.area_id = a.id);

INSERT INTO seat (area_id, row_label, row_rank, number)
SELECT a.id, chr(64 + r), r, n
FROM area a, generate_series(1, 6) r, generate_series(1, 20) n
WHERE a.name = 'Balcony'
AND NOT EXISTS (SELECT 1 FROM seat s WHERE s.area_id = a.id);

INSERT INTO users (username, password_hash, organizer)
SELECT u.username, '$2y$10$j/M/jq8BgMViC6q0D848xOkq27qcKzm.J3fTrmudSNf9f7rY0Nqy.', u.organizer
FROM (VALUES
        ('organizer', true),
        ('gatestaff', false),
        ('riya', false),
        ('arjun', false),
        ('meera', false)) AS u (username, organizer)
WHERE NOT EXISTS (SELECT 1 FROM users x WHERE x.username = u.username);

UPDATE users SET email = username || '@example.com'
WHERE email IS NULL AND username IN ('organizer', 'gatestaff', 'riya', 'arjun', 'meera');

UPDATE users SET email_verified_at = now()
WHERE email_verified_at IS NULL AND username IN ('organizer', 'gatestaff', 'riya', 'arjun', 'meera');

INSERT INTO event (venue_id, organizer_id, title, starts_at, ends_at, booking_opens_at, booking_closes_at, max_per_person,
                   status, category, tagline, description, presented_by, language, age_guidance)
SELECT v.id, u.id, e.title, e.starts, e.starts + e.runs, now() - interval '1 day', e.starts,
       6, 'PUBLISHED', e.category, e.tagline, e.description, e.presented_by, e.language, e.age_guidance
FROM (SELECT *, CASE WHEN starts_in < interval '1 day' THEN now() + starts_in
                     ELSE (date_trunc('day', (now() + starts_in) AT TIME ZONE 'Asia/Kolkata') + interval '19 hours')
                          AT TIME ZONE 'Asia/Kolkata' END AS starts
      FROM (VALUES
        ('Mumbai', 'Ray Classics Screening', interval '1 hour', interval '2 hours 30 minutes', 'Film',
         'Charulata, restored, on the big screen',
         'A free evening screening of Satyajit Ray''s Charulata (1964), shown from a new restoration with English subtitles. It opens with a short talk on how Ray turned a single house into a whole world, and the conversation carries on in the foyer afterwards.',
         'Juhu Film Circle', 'Bengali, English subtitles', '12+'),
        ('Delhi', 'Monsoon Poetry Evening', interval '21 days', interval '2 hours', 'Poetry',
         'Rain songs in Urdu, Hindi and English',
         'Poets read new work about the monsoon, the city and the people who wait for it. Each set is short, and the evening closes with a sarangi interlude while the hall lights stay low.',
         'Hauz Khas Poetry Society', 'Hindi, Urdu, English', 'All ages'),
        ('Bengaluru', 'Startup Stories Talk', interval '28 days', interval '1 hour 45 minutes', 'Talk',
         'Three founders on the year that nearly broke them',
         'An honest evening about building companies in Bengaluru. Three founders each walk through one hard year, what they would change, and then take questions from the floor. No slides and no pitches.',
         'Indiranagar Founders Club', 'English', '16+'),
        ('Pune', 'College Drama Fest', interval '35 days', interval '2 hours 15 minutes', 'Theatre',
         'Four college troupes, four short plays, one night',
         'Student theatre groups from across Pune stage short original plays in Marathi and Hindi. Each piece runs about twenty minutes, with a short break after the second play.',
         'Pune Inter College Theatre Circle', 'Marathi, Hindi', '12+'),
        ('Mumbai', 'Raag at Dusk', interval '9 days', interval '2 hours', 'Music',
         'Sitar and tabla as the light goes',
         'An evening raga recital that follows the hour. The sitar opens slow with an alap, the tabla joins for the faster gat, and the night ends on a short thumri. Seating is close and the hall stays dim.',
         'Bandra Baithak', 'Instrumental', 'All ages'),
        ('Mumbai', 'Open Mic Comedy Night', interval '12 days', interval '1 hour 30 minutes', 'Comedy',
         'Eight new comics, five minutes each',
         'New voices try their sharpest five minutes on a real stage. A host keeps the night moving, and the last set belongs to a headliner working on new material.',
         'Andheri Laugh Room', 'Hindi, English', '16+'),
        ('Delhi', 'Qawwali Under the Stars', interval '14 days', interval '2 hours 30 minutes', 'Music',
         'A full party of voices, harmonium and clapping',
         'A qawwali party sings Amir Khusro and Bulleh Shah late into the evening. The first half is gentle, the second builds until the whole hall is clapping along.',
         'Nizamuddin Music Trust', 'Urdu, Punjabi', 'All ages'),
        ('Delhi', 'Bodies in Motion', interval '18 days', interval '1 hour 20 minutes', 'Dance',
         'A contemporary dance double bill',
         'Two short works by young Delhi choreographers. The first is a duet about leaving home, the second a group piece built from everyday gestures on the metro.',
         'Lodhi Dance Collective', 'No words', '12+'),
        ('Delhi', 'Brass and Breath', interval '30 days', interval '1 hour 40 minutes', 'Music',
         'A wedding band walks into a concert hall',
         'The brass band that plays a thousand baraats a year sits down for one night to play properly. Old film songs, marches and a few surprises, arranged for a seated hall.',
         'Chandni Chowk Brass Society', 'Instrumental', 'All ages'),
        ('Bengaluru', 'Jazz on Church Street', interval '16 days', interval '2 hours', 'Music',
         'A quartet, a long set and no phones out',
         'A late jazz set from a city quartet: standards in the first half, their own tunes in the second. Come early for the soundcheck, which is part of the show.',
         'Church Street Listening Club', 'Instrumental', '16+'),
        ('Bengaluru', 'Eclipse Live', interval '24 days', interval '1 hour 30 minutes', 'Music',
         'Electronic music inside a light installation',
         'A live electronic set played inside a moving ring of light. The room goes fully dark between pieces. Not suited to anyone sensitive to flashing light.',
         'Koramangala Sound Lab', 'Instrumental', '16+'),
        ('Pune', 'Colours in Motion', interval '20 days', interval '2 hours', 'Dance',
         'Six dance groups, six traditions, one stage',
         'Dance groups from across the country share one stage for a night of folk forms. Each group introduces its dance before it begins, so you know what to watch for.',
         'Pune Folk Arts Forum', 'Marathi, Hindi, English', 'All ages'),
        ('Pune', 'Stand Up in Marathi', interval '26 days', interval '1 hour 30 minutes', 'Comedy',
         'Pune jokes for people who know Pune',
         'Four comics on traffic, tea, parents and the eternal question of which side of the river you live on. All in Marathi, with a little English when it is funnier.',
         'Kothrud Comedy Katta', 'Marathi', '16+'))
     AS raw (city, title, starts_in, runs, category, tagline, description, presented_by, language, age_guidance)) e
JOIN venue v ON v.city = e.city
JOIN users u ON u.username = 'organizer'
WHERE NOT EXISTS (SELECT 1 FROM event x WHERE x.venue_id = v.id AND x.title = e.title);

INSERT INTO event (venue_id, organizer_id, title, starts_at, ends_at, booking_opens_at, booking_closes_at, max_per_person,
                   status, category, tagline, description, presented_by, language, age_guidance, waiting_room)
SELECT v.id, u.id, e.title, e.starts, e.starts + e.runs, now() + e.opens_in, e.starts,
       4, 'PUBLISHED', 'Music', e.tagline, e.description, 'Big Night Live', e.language, 'All ages', true
FROM (SELECT *, (date_trunc('day', (now() + starts_in) AT TIME ZONE 'Asia/Kolkata') + interval '19 hours 30 minutes')
                AT TIME ZONE 'Asia/Kolkata' AS starts
      FROM (VALUES
        ('Mumbai', 'Raunak Sen Live', interval '40 days', interval '15 minutes', interval '2 hours 30 minutes',
         'One voice and a hall that sings every word back',
         'A rare seated night in a concert hall instead of a stadium. Two sets of the songs everyone knows, a quiet acoustic stretch in the middle, and an encore the band never announces. Seats open all at once and go in minutes, so have your group ready.',
         'Hindi, Bengali'),
        ('Delhi', 'Gurnoor Unplugged', interval '45 days', interval '30 minutes', interval '2 hours',
         'The big hits, stripped back to guitar and dhol',
         'A small hall version of the biggest tour in the country. Fewer lights, more stories between songs, and the whole room on its feet by the end. Booking opens at a set time and every seat is gone almost at once.',
         'Punjabi, Hindi'),
        ('Bengaluru', 'Kabir Mathur Acoustic Night', interval '50 days', interval '45 minutes', interval '1 hour 45 minutes',
         'Quiet songs, a full room, no phones up',
         'Just a guitar, a piano and the songs from every playlist you made in college. The hall is asked to keep phones away. Seats open at a set time and are usually gone within the hour.',
         'English, Hindi'),
        ('Pune', 'Ilan Varma Piano Sessions', interval '55 days', interval '1 hour', interval '2 hours 15 minutes',
         'Film scores you grew up with, played on one piano',
         'A night where the composer sits at the piano and walks through three decades of film music, telling where each tune came from. A small string section joins for the second half. Expect every seat to go the moment booking opens.',
         'Instrumental, Tamil, Hindi'),
        ('Mumbai', 'Tara Raghavan Strings Live', interval '60 days', interval '2 hours', interval '1 hour 50 minutes',
         'Sitar, strings and electronics in one long arc',
         'The sitar player brings her new album to a seated hall, moving from raga to film score to electronic textures without a break. A listening concert, best heard from the stalls.',
         'Instrumental'))
     AS raw (city, title, starts_in, opens_in, runs, tagline, description, language)) e
JOIN venue v ON v.city = e.city
JOIN users u ON u.username = 'organizer'
WHERE NOT EXISTS (SELECT 1 FROM event x WHERE x.venue_id = v.id AND x.title = e.title);

INSERT INTO performer (event_id, position, name, role)
SELECT e.id, p.position, p.name, p.role
FROM (VALUES
        ('Ray Classics Screening', 1, 'Charulata (1964)', 'Feature film, directed by Satyajit Ray'),
        ('Ray Classics Screening', 2, 'Meher Kapadia', 'Film historian, introduction'),
        ('Ray Classics Screening', 3, 'Arun Bhide', 'Host, foyer conversation'),
        ('Monsoon Poetry Evening', 1, 'Aanya Sethi', 'Urdu ghazals'),
        ('Monsoon Poetry Evening', 2, 'Kabir Malhotra', 'Hindi poetry'),
        ('Monsoon Poetry Evening', 3, 'Rhea D''Souza', 'English spoken word'),
        ('Monsoon Poetry Evening', 4, 'Dhruv Nair', 'Sarangi'),
        ('Startup Stories Talk', 1, 'Nisha Rao', 'Moderator'),
        ('Startup Stories Talk', 2, 'Arvind Iyer', 'Founder, logistics software'),
        ('Startup Stories Talk', 3, 'Sana Qureshi', 'Founder, community health'),
        ('Startup Stories Talk', 4, 'Vikram Shetty', 'Founder, regional language publishing'),
        ('College Drama Fest', 1, 'Rangmanch Collective', 'Opening play'),
        ('College Drama Fest', 2, 'Kala Ankur Players', 'Second play'),
        ('College Drama Fest', 3, 'Natya Darpan', 'Third play'),
        ('College Drama Fest', 4, 'Ekank Studio', 'Closing play'),
        ('Raag at Dusk', 1, 'Ishaan Kulkarni', 'Sitar'),
        ('Raag at Dusk', 2, 'Farhan Shaikh', 'Tabla'),
        ('Raag at Dusk', 3, 'Leela Menon', 'Tanpura'),
        ('Open Mic Comedy Night', 1, 'Tara Joshi', 'Host'),
        ('Open Mic Comedy Night', 2, 'Rohan Bakshi', 'Headliner'),
        ('Open Mic Comedy Night', 3, 'Eight open mic comics', 'Five minutes each'),
        ('Qawwali Under the Stars', 1, 'Warsi Brothers Party', 'Qawwali'),
        ('Qawwali Under the Stars', 2, 'Imran Ali', 'Harmonium'),
        ('Qawwali Under the Stars', 3, 'Salim Qadri', 'Dholak'),
        ('Bodies in Motion', 1, 'Nandini Rawat', 'Choreographer, Leaving'),
        ('Bodies in Motion', 2, 'Aditya Bose', 'Choreographer, Blue Line'),
        ('Bodies in Motion', 3, 'Lodhi Dance Collective', 'Ensemble'),
        ('Brass and Breath', 1, 'Master Jamal Khan', 'Bandmaster, trumpet'),
        ('Brass and Breath', 2, 'Chandni Chowk Brass Society', 'Twelve piece band'),
        ('Jazz on Church Street', 1, 'Maya Pinto', 'Saxophone'),
        ('Jazz on Church Street', 2, 'Karthik Rao', 'Piano'),
        ('Jazz on Church Street', 3, 'Joel Fernandes', 'Double bass'),
        ('Jazz on Church Street', 4, 'Anil Gowda', 'Drums'),
        ('Eclipse Live', 1, 'Sonar Kite', 'Live electronics'),
        ('Eclipse Live', 2, 'Reema Thomas', 'Light design'),
        ('Colours in Motion', 1, 'Lavani Kala Mandal', 'Lavani'),
        ('Colours in Motion', 2, 'Ghoomar Sangam', 'Ghoomar'),
        ('Colours in Motion', 3, 'Bihu Xobha', 'Bihu'),
        ('Colours in Motion', 4, 'Three guest troupes', 'Garba, Chhau, Yakshagana'),
        ('Stand Up in Marathi', 1, 'Sai Deshpande', 'Host'),
        ('Stand Up in Marathi', 2, 'Omkar Patwardhan', 'Comic'),
        ('Stand Up in Marathi', 3, 'Gauri Phadke', 'Comic'),
        ('Stand Up in Marathi', 4, 'Nikhil Gokhale', 'Headliner'),
        ('Raunak Sen Live', 1, 'Raunak Sen', 'Vocals'),
        ('Raunak Sen Live', 2, 'Full live band', 'Guitars, keys, percussion'),
        ('Gurnoor Unplugged', 1, 'Gurnoor', 'Vocals'),
        ('Gurnoor Unplugged', 2, 'Unplugged band', 'Guitar, dhol, tumbi'),
        ('Kabir Mathur Acoustic Night', 1, 'Kabir Mathur', 'Vocals, guitar'),
        ('Kabir Mathur Acoustic Night', 2, 'Trio', 'Piano, cello, drums'),
        ('Ilan Varma Piano Sessions', 1, 'Ilan Varma', 'Piano, vocals'),
        ('Ilan Varma Piano Sessions', 2, 'Chamber strings', 'Second half'),
        ('Tara Raghavan Strings Live', 1, 'Tara Raghavan', 'Sitar'),
        ('Tara Raghavan Strings Live', 2, 'Ensemble', 'Strings, electronics, percussion'))
     AS p (title, position, name, role)
JOIN event e ON e.title = p.title
WHERE NOT EXISTS (SELECT 1 FROM performer x WHERE x.event_id = e.id AND x.position = p.position);

INSERT INTO event_area (event_id, area_id)
SELECT e.id, a.id
FROM event e
JOIN area a ON a.venue_id = e.venue_id
WHERE NOT EXISTS (SELECT 1 FROM event_area x WHERE x.event_id = e.id AND x.area_id = a.id);

INSERT INTO staff_assignment (event_id, user_id)
SELECT e.id, u.id
FROM event e
JOIN venue v ON v.id = e.venue_id AND v.city = 'Mumbai'
JOIN users u ON u.username = 'gatestaff'
WHERE NOT EXISTS (SELECT 1 FROM staff_assignment x WHERE x.event_id = e.id AND x.user_id = u.id);

INSERT INTO booking (event_id, user_id, request_id)
SELECT e.id, u.id, gen_random_uuid()
FROM event e
JOIN users u ON u.username IN ('organizer', 'meera')
WHERE e.title = 'Ray Classics Screening'
AND NOT EXISTS (SELECT 1 FROM booking x WHERE x.event_id = e.id AND x.user_id = u.id);

INSERT INTO ticket (booking_id, event_id, seat_id, attendee_name, status, code)
SELECT b.id, b.event_id, s.id, 'Guest', 'VALID',
       upper(translate(substr(md5(random()::text || s.id::text), 1, 10), '01', 'gh'))
FROM booking b
JOIN users u ON u.id = b.user_id AND u.username = 'organizer'
JOIN event e ON e.id = b.event_id
JOIN area a ON a.venue_id = e.venue_id AND a.name = 'Balcony'
JOIN seat s ON s.area_id = a.id
WHERE (s.row_label, s.number) NOT IN (('B', 5), ('D', 12), ('F', 18), ('C', 10), ('C', 11))
AND NOT EXISTS (SELECT 1 FROM ticket x WHERE x.event_id = b.event_id AND x.seat_id = s.id);

INSERT INTO ticket (booking_id, event_id, seat_id, attendee_name, status, code)
SELECT b.id, b.event_id, s.id, 'meera', 'VALID',
       upper(translate(substr(md5(random()::text || s.id::text), 1, 10), '01', 'gh'))
FROM booking b
JOIN users u ON u.id = b.user_id AND u.username = 'meera'
JOIN event e ON e.id = b.event_id
JOIN area a ON a.venue_id = e.venue_id AND a.name = 'Balcony'
JOIN seat s ON s.area_id = a.id
WHERE (s.row_label, s.number) IN (('C', 10), ('C', 11))
AND NOT EXISTS (SELECT 1 FROM ticket x WHERE x.event_id = b.event_id AND x.seat_id = s.id);

UPDATE event_area ea SET price_paise = CASE a.name WHEN 'Stalls Front' THEN 299900 WHEN 'Stalls Rear' THEN 199900 ELSE 99900 END
FROM area a, event e
WHERE a.id = ea.area_id AND e.id = ea.event_id AND e.waiting_room AND ea.price_paise = 0;
