/**
 * Seed script for TableFor Firebase Cloud Firestore (tablefor-b8d20)
 *
 * To run via Node.js with Firebase Admin SDK:
 * 1. Generate a service account key from Firebase Console -> Project Settings -> Service Accounts.
 * 2. Save it as `serviceAccountKey.json` in this directory.
 * 3. Run `npm install firebase-admin` and `node scripts/seed_firestore.js`.
 */

const admin = require('firebase-admin');

try {
  const serviceAccount = require('./serviceAccountKey.json');
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
} catch (e) {
  // Fallback to default application credentials if available
  admin.initializeApp({
    projectId: 'tablefor-b8d20'
  });
}

const db = admin.firestore();

async function seedDatabase() {
  console.log('Seeding tablefor-b8d20 Firestore database...');

  // 1. Seed User Profile
  await db.collection('users').doc('user_1').set({
    id: 'user_1',
    name: 'Alex Chen',
    bio: 'New in town, love exploring food spots!',
    location: 'Downtown / Financial District',
    diets: ['Vegetarian'],
    cuisinePreferences: ['Italian', 'Japanese', 'Mexican'],
    budget: '$$ (Moderate)',
    trustRating: 4.8,
    totalRatings: 12,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });
  console.log('✔ users/user_1 created');

  // 2. Seed Schedule
  await db.collection('schedules').doc('user_1').set({
    userId: 'user_1',
    cells: [
      { dayIndex: 3, dayLabel: 'Thu, Oct 16', timeSlot: '7:00 PM', isAvailable: true },
      { dayIndex: 3, dayLabel: 'Thu, Oct 16', timeSlot: '8:00 PM', isAvailable: true },
      { dayIndex: 4, dayLabel: 'Fri, Oct 17', timeSlot: '7:00 PM', isAvailable: true },
      { dayIndex: 4, dayLabel: 'Fri, Oct 17', timeSlot: '8:00 PM', isAvailable: true }
    ],
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });
  console.log('✔ schedules/user_1 created');

  // 3. Seed Dinner Group
  await db.collection('dinner_groups').doc('group_101').set({
    groupId: 'group_101',
    restaurantName: 'Trattoria Bella',
    cuisine: 'Italian',
    neighborhood: 'Downtown',
    address: '142 5th Ave, New York, NY 10011',
    timeString: 'Thursday, 7:00 PM',
    status: 'SCHEDULED',
    memberIds: ['1', '2', '3', '4'],
    members: [
      { id: '1', name: 'Alex Chen', role: 'You', rating: 4.8, diet: 'Vegetarian' },
      { id: '2', name: 'Sarah Jenkins', role: 'Student @ NYU', rating: 4.9, diet: 'None' },
      { id: '3', name: 'Marcus Vance', role: 'Software Engineer', rating: 4.7, diet: 'Vegan' },
      { id: '4', name: 'Elena Rostova', role: 'Designer', rating: 5.0, diet: 'Gluten-Free' }
    ],
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });
  console.log('✔ dinner_groups/group_101 created');

  // 4. Seed Chat Messages
  const messagesRef = db.collection('dinner_groups').doc('group_101').collection('messages');
  await messagesRef.doc('msg_1').set({
    id: 'msg_1',
    groupId: 'group_101',
    senderId: '2',
    senderName: 'Sarah Jenkins',
    text: 'Hey everyone! Excited for dinner on Thursday at Trattoria Bella!',
    timestamp: '6:45 PM',
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });
  await messagesRef.doc('msg_2').set({
    id: 'msg_2',
    groupId: 'group_101',
    senderId: '3',
    senderName: 'Marcus Vance',
    text: 'Me too! Are we all good with walking there together?',
    timestamp: '6:50 PM',
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });
  await messagesRef.doc('msg_3').set({
    id: 'msg_3',
    groupId: 'group_101',
    senderId: '1',
    senderName: 'Alex Chen',
    text: 'Sounds great! See you all there.',
    timestamp: '6:52 PM',
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  });
  console.log('✔ dinner_groups/group_101/messages created');

  // 5. Seed Member Ratings
  const ratingsRef = db.collection('dinner_groups').doc('group_101').collection('ratings');
  await ratingsRef.doc('group_101_1_2').set({
    ratingId: 'group_101_1_2',
    groupId: 'group_101',
    raterUserId: '1',
    targetUserId: '2',
    memberName: 'Sarah Jenkins',
    punctuality: 5,
    friendliness: 5,
    reliability: 5,
    notes: 'Great conversation and on time!',
    submittedAt: admin.firestore.FieldValue.serverTimestamp()
  });
  console.log('✔ dinner_groups/group_101/ratings created');

  console.log('✅ Firestore seeding complete!');
}

seedDatabase().catch(console.error);
