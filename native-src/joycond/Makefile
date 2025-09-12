CC ?= aarch64-linux-gnu-gcc
CFLAGS = -O2 -Wall
TARGET = joycond

all:
	$(CC) $(CFLAGS) -o $(TARGET) main.c -lpthread

clean:
	rm -f $(TARGET)
