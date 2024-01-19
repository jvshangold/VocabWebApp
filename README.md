# Hello
My name is Jerome Shangold. I'm a third year undergraduate at Johns Hopkins.
The work done in this repository is a personal project that is an extension on a
lab for the **Software Construction** course at EPFL.

## My goal 
in this project was to make a centralized web app that can accept new
words – and their translations – from multiple users, and then allow those users
to quiz themselves on those words. To do this, I had to implement a state machine
to keep the internal state of the app, implement a view type for each user to have
their own view of the state, serialize and deserialize events and views into JSON
using the ujson library, and create a UI using scala.js for the user to interact with.

### I am responsible for the code in:
- VocabLogic.scala
- VocabWire.scala
- VocabTypes.scala
- VocabUI.scala
- VocabTest.scala
- (and some modifications in sbt files)

Everything else is the work of the teaching staff at EPFL. 

#### Feel free to download the code and practice language vocab with your friends.
(on the same local network of course)

