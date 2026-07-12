int p1 = 3;
int led = 13;

void setup() {
  Serial.begin(9600);
  pinMode(p1, INPUT);
  pinMode(led, OUTPUT);
}

void loop() {
  int valor = digitalRead(p1);
  Serial.print("Valor del pulsador: ");
  Serial.println(valor);
  digitalWrite(led, valor);
  delay(500);
}
