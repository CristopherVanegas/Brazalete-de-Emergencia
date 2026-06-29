#include <SoftwareSerial.h>

// HC-05
// TXD HC-05 -> Pin 10 Arduino
// RXD HC-05 -> Pin 11 Arduino con divisor de voltaje
SoftwareSerial BT(10, 11);

const int PIN_ALERTA = 3;
const int PIN_DESCONECTAR = 4;
const int LED = 13;

bool estadoAnteriorAlerta = LOW;
bool estadoAnteriorDesconectar = LOW;

void setup() {
  pinMode(PIN_ALERTA, INPUT);
  pinMode(PIN_DESCONECTAR, INPUT);
  pinMode(LED, OUTPUT);

  Serial.begin(9600);
  BT.begin(9600);

  Serial.println("Sistema iniciado.");
  Serial.println("Pin 3 HIGH -> envia S");
  Serial.println("Pin 4 HIGH -> envia D para desconectar Bluetooth");
}

void loop() {
  bool estadoAlerta = digitalRead(PIN_ALERTA);
  bool estadoDesconectar = digitalRead(PIN_DESCONECTAR);

  // Pin 3: enviar señal de alerta
  if (estadoAlerta == HIGH && estadoAnteriorAlerta == LOW) {
    BT.write('S');

    Serial.println("HIGH detectado en pin 3. Se envio: S");

    digitalWrite(LED, HIGH);
    delay(300);
    digitalWrite(LED, LOW);
  }

  // Pin 4: pedir desconexión Bluetooth a la app
  if (estadoDesconectar == HIGH && estadoAnteriorDesconectar == LOW) {
    BT.write('D');

    Serial.println("HIGH detectado en pin 4. Se envio: D");

    digitalWrite(LED, HIGH);
    delay(100);
    digitalWrite(LED, LOW);
    delay(100);
    digitalWrite(LED, HIGH);
    delay(100);
    digitalWrite(LED, LOW);
  }

  estadoAnteriorAlerta = estadoAlerta;
  estadoAnteriorDesconectar = estadoDesconectar;
}